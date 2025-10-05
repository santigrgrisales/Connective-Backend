package com.fg.importcsv.controller;

import com.fg.importcsv.service.CsvImportService;
import com.fg.importcsv.service.CsvImportService.ImportResult;
import com.fg.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(
        origins = "http://localhost:5500",
        allowedHeaders = {"Authorization", "Content-Type", "Accept", "multipart/form-data"},
        methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS},
        allowCredentials = "true"
)
@RequestMapping("/api/archivos")
public class CsvImportController {

    private static final Logger log = LoggerFactory.getLogger(CsvImportController.class);

    private final CsvImportService csvImportService;
    private final UserRepository userRepository; 
    private final JdbcTemplate jdbcTemplate; // 👈 para consultar directamente la tabla

    public CsvImportController(CsvImportService csvImportService, UserRepository userRepository, JdbcTemplate jdbcTemplate) {
        this.csvImportService = csvImportService;
        this.userRepository = userRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "nombreArchivo", required = false) String nombreArchivo,
            @RequestParam(value = "description", required = false) String description,
            Authentication authentication
    ) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("Se requiere un archivo CSV no vacío");
        }

        try {
            // Sacamos el email del token
            String email = authentication.getName();

            // Buscamos el id en la base de datos
            Long userId = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Usuario no encontrado"))
                    .getId();

            String nombre = (nombreArchivo == null || nombreArchivo.isBlank())
                    ? file.getOriginalFilename()
                    : nombreArchivo;

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

    @GetMapping("/list")
    public ResponseEntity<List<Map<String, Object>>> listarArchivos() {
        try {
            String sql = "SELECT id_archivo, nombre_archivo, description, created_at FROM archivos ORDER BY created_at DESC";
            List<Map<String, Object>> archivos = jdbcTemplate.queryForList(sql);
            return ResponseEntity.ok(archivos);
        } catch (Exception e) {
            log.error("Error al listar archivos: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(null);
        }
    }

    @GetMapping("/ping")
    public String ping() {
        return "pong";
    }
}

