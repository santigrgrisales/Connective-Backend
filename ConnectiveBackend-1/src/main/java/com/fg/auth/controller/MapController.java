package com.fg.auth.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/api/map")
public class MapController {
@GetMapping("/view")
@PreAuthorize("hasAnyRole('CONSULTOR','ADMIN')")
public String verMapa() {
return "Aquí van los datos del mapa";
}
@GetMapping("/identify")
@PreAuthorize("hasAnyRole('CONSULTOR','ADMIN')")
public String identificarPrioridades() {
	return "Función de identificación";
}
}