package com.fg.importcsv.controller;

import com.fg.importcsv.service.CsvImportService;
import com.fg.importcsv.service.CsvImportService.ImportResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/archivos")
public class CsvImportController {

    private static final Logger log = LoggerFactory.getLogger(CsvImportController.class);

    private final CsvImportService csvImportService;

    public CsvImportController(CsvImportService csvImportService) {
        this.csvImportService = csvImportService;
    }

    /**
     * Upload CSV and import into DB.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") Long userId,
            @RequestParam(value = "nombreArchivo", required = false) String nombreArchivo,
            @RequestParam(value = "description", required = false) String description
    ) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("Se requiere un archivo CSV no vacío");
        }
        if (userId == null) {
            return ResponseEntity.badRequest().body("Se requiere userId");
        }
        try {
            String nombre = (nombreArchivo == null || nombreArchivo.isBlank()) ? file.getOriginalFilename() : nombreArchivo;
            ImportResult res = csvImportService.importCsv(file, userId, nombre, description);
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException iae) {
            log.warn("CSV inválido: {}", iae.getMessage());
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (Exception e) {
            log.error("Error al procesar CSV: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("Error al procesar el CSV: " + e.getMessage());
        }
    }

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }
}
