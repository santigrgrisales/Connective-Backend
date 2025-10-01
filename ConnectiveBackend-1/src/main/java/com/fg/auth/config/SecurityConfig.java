package com.fg.auth.config;

import com.fg.auth.filter.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;


@Configuration
@EnableMethodSecurity
public class SecurityConfig {


private final JwtAuthenticationFilter jwtAuthFilter;


public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter) {
this.jwtAuthFilter = jwtAuthFilter;
}


@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
http
.csrf().disable()
.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
.and()
.authorizeHttpRequests()
.requestMatchers("/api/auth/**").permitAll()
.requestMatchers("/api/map/**").hasAnyRole("CONSULTOR","ADMIN")
.requestMatchers("/api/data/upload").hasRole("ADMIN")
.anyRequest().authenticated()
.and()
.addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);


return http.build();
}


@Bean
public PasswordEncoder passwordEncoder() {
return new BCryptPasswordEncoder();
}


@Bean
public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
return config.getAuthenticationManager();
}



}
