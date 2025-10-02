package com.fg.auth.controller;

import com.fg.auth.dto.LoginRequest;
import com.fg.auth.dto.RegisterRequest;
import com.fg.auth.jwt.JwtUtil;
import com.fg.auth.model.User;
import com.fg.auth.repository.UserRepository;
import com.fg.auth.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;


import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final AuthService authService;

    // Inyectamos UserRepository vía campo para no tocar la firma del constructor existente
    @Autowired
    private UserRepository userRepository;

    public AuthController(AuthenticationManager authenticationManager, JwtUtil jwtUtil, AuthService authService) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.authService = authService;
    }

    /**
     * Registro que además realiza login automático: devuelve token + user info.
     * Asume que authService.registerConsultor(dto) crea el usuario con rol CONSULTOR.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest dto) {
        User createdUser = authService.registerConsultor(dto);

        Authentication auth = null;
        try {
            auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(dto.getEmail(), dto.getPassword())
            );
        } catch (Exception ex) {
            // Si la autenticación falla justo después de crear el usuario,
            // devolvemos igualmente token + datos desde la entidad persistida.
        }

        String token = jwtUtil.generateToken(createdUser.getEmail());

        Map<String, Object> body = buildAuthResponseBody(createdUser, auth, token);

        return ResponseEntity.ok(body);
    }

    /**
     * Login: autentica, genera token y devuelve token + user info (email, roles, id, fullname, enabled).
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword())
        );

        String token = jwtUtil.generateToken(req.getEmail());

        // 1) Intentamos obtener la entidad User completa desde la BD (preferred)
        User userFromDb = null;
        try {
            Optional<User> opt = userRepository.findByEmail(req.getEmail());
            if (opt.isPresent()) {
                userFromDb = opt.get();
            }
        } catch (Exception ignore) {
            // si no existe repo o falla por alguna razón, lo ignoramos y usamos principal como fallback
        }

        // 2) Si no encontramos la entidad por repo, intentamos extraerla desde auth.getPrincipal()
        if (userFromDb == null) {
            Object principal = auth.getPrincipal();
            if (principal instanceof User) {
                userFromDb = (User) principal;
            }
        }

        Map<String, Object> body = buildAuthResponseBody(userFromDb, auth, token);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }

    /**
     * Helper para construir la respuesta uniforme { token, user: { ... } }.
     * Si 'user' es null usa lo mínimo disponible desde Authentication.
     */
    private Map<String, Object> buildAuthResponseBody(User user, Authentication auth, String token) {
        Map<String, Object> userMap = new HashMap<>();

        if (user != null) {
            userMap.put("id", user.getId());
            userMap.put("email", user.getEmail());
            // Ajusta el getter si tu entidad usa getFullName()
            try {
                userMap.put("fullname", user.getFullName());
            } catch (Exception e) {
                // Si el getter tiene otro nombre, puedes cambiar aquí getFullname() por getFullName()
            }
            userMap.put("enabled", user.isEnabled());
        } else {
            userMap.put("email", auth != null ? auth.getName() : null);
        }

        // Roles: preferimos Authentication (si existe). Si no, intentamos extraer de la entidad User vía reflexión
        List<String> roles = new ArrayList<>();
        if (auth != null && auth.getAuthorities() != null) {
            roles = auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList());
        } else if (user != null) {
            try {
                Object rolesObj = user.getRoles();
                if (rolesObj instanceof Collection) {
                    Collection<?> c = (Collection<?>) rolesObj;
                    for (Object r : c) {
                        try {
                            java.lang.reflect.Method m = r.getClass().getMethod("getName");
                            Object name = m.invoke(r);
                            if (name != null) roles.add(name.toString());
                        } catch (Exception ignore) {}
                    }
                }
            } catch (Exception ignore) {}
        }
        userMap.put("roles", roles);

        Map<String, Object> body = new HashMap<>();
        body.put("token", token);
        body.put("user", userMap);
        return body;
    }
    
    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        // 1) Validación básica: debe existir Authentication y estar autenticado
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized"));
        }

        // 2) Email del sujeto (normalmente el sub/username)
        String email = authentication.getName();
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized - no email"));
        }

        // 3) Buscar entidad User en BD (usa tu userRepository que ya inyectaste en el controller)
        Optional<User> optionalUser = Optional.empty();
        try {
            optionalUser = userRepository.findByEmail(email);
        } catch (Exception e) {
            // si por alguna razón falla la consulta, devolvemos 500 (o el manejo que prefieras)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error reading user from DB"));
        }

        if (optionalUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "User not found"));
        }

        User user = optionalUser.get();

        // 4) Extraer roles desde Authentication (preferible)
        List<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        // 5) Construir el objeto de respuesta con los campos útiles
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("email", user.getEmail());

        // fullname: ajusta el getter si tu entidad tiene getFullName() en lugar de getFullname()
        try {
            userMap.put("fullname", user.getFullName());
        } catch (Exception ignore) { /* si no existe, se omite */ }

        userMap.put("enabled", user.isEnabled());
        userMap.put("roles", roles);

        return ResponseEntity.ok(userMap);
    }
}
