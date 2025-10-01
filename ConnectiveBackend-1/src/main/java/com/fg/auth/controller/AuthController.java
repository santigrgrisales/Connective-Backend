package com.fg.auth.controller;

import com.fg.auth.dto.LoginRequest;
import com.fg.auth.dto.LoginResponse;
import com.fg.auth.dto.RegisterRequest;
import com.fg.auth.jwt.JwtUtil;
import com.fg.auth.model.User;
import com.fg.auth.service.AuthService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@CrossOrigin(origins = "http://localhost:5500") // ajusta el puerto/origen donde sirves tu HTML
@RequestMapping("/api/auth")
public class AuthController {
private final AuthenticationManager authenticationManager;
private final JwtUtil jwtUtil;
private final AuthService authService;
public AuthController(AuthenticationManager authenticationManager, JwtUtil
jwtUtil, AuthService authService) {
this.authenticationManager = authenticationManager;
this.jwtUtil = jwtUtil;
this.authService = authService;
}
@PostMapping("/register")
public String register(@RequestBody RegisterRequest dto) {
User u = authService.registerConsultor(dto);
return "Usuario registrado: " + u.getEmail();
}
@PostMapping("/login")
public LoginResponse login(@RequestBody LoginRequest req) {
Authentication auth = authenticationManager.authenticate(new
UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword()));
String token = jwtUtil.generateToken(req.getEmail());
return new LoginResponse(token);
}

@GetMapping("/ping")
public String ping() {
    return "pong";
}

}

