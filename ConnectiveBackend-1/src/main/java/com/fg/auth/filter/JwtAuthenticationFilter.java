package com.fg.auth.filter;

import com.fg.auth.jwt.JwtUtil;
import com.fg.auth.service.UserDetailsServiceImpl;
import
org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import
org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
private final JwtUtil jwtUtil;
private final UserDetailsServiceImpl userDetailsService;
public JwtAuthenticationFilter(JwtUtil jwtUtil, UserDetailsServiceImpl
userDetailsService) {
this.jwtUtil = jwtUtil;
this.userDetailsService = userDetailsService;
}
@Override
protected void doFilterInternal(HttpServletRequest request,
HttpServletResponse response, FilterChain chain)
throws IOException, jakarta.servlet.ServletException {
String header = request.getHeader("Authorization");
if (header != null && header.startsWith("Bearer ")) {
String token = header.substring(7);
if (jwtUtil.validateJwtToken(token)) {
String email = jwtUtil.getEmailFromToken(token);
UserDetails userDetails =
userDetailsService.loadUserByUsername(email);
UsernamePasswordAuthenticationToken auth = new
UsernamePasswordAuthenticationToken(
userDetails, null, userDetails.getAuthorities());
auth.setDetails(new
WebAuthenticationDetailsSource().buildDetails(request));
SecurityContextHolder.getContext().setAuthentication(auth);
}
}
chain.doFilter(request, response);
}
}