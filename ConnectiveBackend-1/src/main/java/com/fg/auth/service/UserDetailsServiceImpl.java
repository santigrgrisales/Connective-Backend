package com.fg.auth.service;

import com.fg.auth.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import java.util.stream.Collectors;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {
private final UserRepository userRepository;
public UserDetailsServiceImpl(UserRepository userRepository) {
this.userRepository = userRepository;
}
@Override
public UserDetails loadUserByUsername(String email) throws
UsernameNotFoundException {
var user = userRepository.findByEmail(email).orElseThrow(()-> new
UsernameNotFoundException("Usuario no encontrado"));
var authorities = user.getRoles().stream()
.map(r-> new SimpleGrantedAuthority(r.getName()))
.collect(Collectors.toList());
return new
org.springframework.security.core.userdetails.User(user.getEmail(),
user.getPassword(), user.isEnabled(), true, true, true, authorities);
}
}

