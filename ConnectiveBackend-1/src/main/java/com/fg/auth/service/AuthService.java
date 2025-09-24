package com.fg.auth.service;

import com.fg.auth.dto.RegisterRequest;
import com.fg.auth.repository.RoleRepository;
import com.fg.auth.repository.UserRepository;
import com.fg.auth.model.User;
import com.fg.auth.model.RoleEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.Collections;


@Service
public class AuthService {
private final UserRepository userRepository;
private final PasswordEncoder passwordEncoder;
private final RoleRepository roleRepository;


public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, RoleRepository roleRepository) {
this.userRepository = userRepository;
this.passwordEncoder = passwordEncoder;
this.roleRepository = roleRepository;
}


public User registerConsultor(RegisterRequest dto) {
if (userRepository.existsByEmail(dto.getEmail())) throw new RuntimeException("Email ya registrado");
User u = new User();
u.setEmail(dto.getEmail());
u.setPassword(passwordEncoder.encode(dto.getPassword()));
u.setFullName(dto.getFullName());


RoleEntity rol = roleRepository.findByName("ROLE_CONSULTOR").orElseThrow();
u.setRoles(Collections.singleton(rol));
return userRepository.save(u);
}
}
