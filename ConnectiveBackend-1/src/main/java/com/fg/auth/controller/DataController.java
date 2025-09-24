package com.fg.auth.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/api/data")
public class DataController {
	
	
@PostMapping("/upload")
@PreAuthorize("hasRole('ADMIN')")
public String uploadData() {
return "Datos subidos";
}
}