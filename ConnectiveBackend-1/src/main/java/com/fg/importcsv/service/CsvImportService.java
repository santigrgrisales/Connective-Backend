package com.fg.importcsv.service;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.text.Normalizer;
import java.util.*;

@Service
public class CsvImportService {

    private static final Logger log = LoggerFactory.getLogger(CsvImportService.class);

    private final JdbcTemplate jdbcTemplate;
    private static final int BATCH_SIZE = 3000; // Ajustable según memoria / rendimiento

    public CsvImportService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public static class ImportResult {
        public long idArchivo;
        public int totalRows;
        public int insertedRows;
        public int skippedRows;
        public List<String> errors = new ArrayList<>();
    }

    private static final List<String> REQUIRED_HEADERS = Arrays.asList(
            "ID_Hogar","Departamento_ISO","Ingreso_Categoria","Razon_No_Internet",
            "Indice_Habilidades_Digitales","Tipo_Uso_Internet","Calidad_Servicio_Percibida",
            "Barrera_Costo","N_Personas","N_5_14","N_15_24","N_25_60","N_Mayores_60","N_Mujeres","N_Hombres"
    );

    @Transactional
    public ImportResult importCsv(MultipartFile file, Long userId, String nombreArchivo, String description) throws Exception {
        log.info("Iniciando importación CSV: file={}, userId={}, nombreArchivo={}", file.getOriginalFilename(), userId, nombreArchivo);
        ImportResult result = new ImportResult();

        // 1) Crear fila en 'archivos' y obtener id_archivo
        String insertArchivoSql = "INSERT INTO archivos (id_user, nombre_archivo, description) VALUES (?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var ps = connection.prepareStatement(insertArchivoSql, new String[]{"id_archivo"});
            ps.setLong(1, userId);
            ps.setString(2, nombreArchivo);
            ps.setString(3, description);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            log.error("No se pudo generar id_archivo");
            throw new IllegalStateException("No se pudo generar id_archivo");
        }
        long idArchivo = key.longValue();
        result.idArchivo = idArchivo;
        log.info("Registro de archivo creado con id_archivo={}", idArchivo);

        // 2) Cache de regiones: codigo_iso -> id_region, normalized(name) -> id_region
        Map<String, Long> regionByIso = new HashMap<>();
        Map<String, Long> regionByNameNormalized = new HashMap<>();
        jdbcTemplate.query("SELECT id_region, codigo_iso, name_region FROM regions",
                rs -> {
                    Long id = rs.getLong("id_region");
                    String iso = rs.getString("codigo_iso");
                    String name = rs.getString("name_region");
                    if (iso != null && !iso.trim().isEmpty()) regionByIso.put(iso.trim().toUpperCase(), id);
                    if (name != null && !name.trim().isEmpty()) regionByNameNormalized.put(normalizeName(name), id);
                });
        log.info("Regiones cacheadas: iso={}, name={}", regionByIso.size(), regionByNameNormalized.size());

        // 3) Parse CSV and batch insert
        final String insertHogarSql =
                "INSERT INTO hogares (" +
                        "id_archivo, id_region, household_code, ingreso_categoria, razon_no_internet, indice_habilidades, " +
                        "tipo_uso_internet, calidad_servicio, n_personas, n_5_14, n_15_24, n_25_60, n_mayores_60, n_mujeres, n_hombres, barrera_costo" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        int totalRows = 0;
        int insertedRows = 0;
        int skippedRows = 0;

        List<Map<String,Object>> buffer = new ArrayList<>(BATCH_SIZE);

        try (Reader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            CSVParser parser = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .withIgnoreEmptyLines()
                    .withTrim()
                    .parse(reader);

            // validate headers
            Map<String,Integer> headerMap = parser.getHeaderMap();
            for (String required : REQUIRED_HEADERS) {
                if (!headerMap.containsKey(required)) {
                    throw new IllegalArgumentException("CSV faltante columna requerida: " + required);
                }
            }

            for (CSVRecord rec : parser) {
                totalRows++;
                try {
                    String departamentoIsoOrName = safeTrim(rec.get("Departamento_ISO"));
                    Long idRegion = null;

                    if (departamentoIsoOrName != null && !departamentoIsoOrName.isEmpty()) {
                        // Primero intentar como ISO
                        Long byIso = regionByIso.get(departamentoIsoOrName.trim().toUpperCase());
                        if (byIso != null) {
                            idRegion = byIso;
                        } else {
                            // intentar name normalized (el CSV a veces trae el nombre)
                            String normalized = normalizeName(departamentoIsoOrName);
                            Long byName = regionByNameNormalized.get(normalized);
                            if (byName != null) {
                                idRegion = byName;
                            } else {
                                // fallback: buscar en DB y cachear
                                Long dbId = findRegionIdByIsoOrName(departamentoIsoOrName);
                                if (dbId != null) {
                                    idRegion = dbId;
                                    // cache both representations
                                    regionByIso.put(departamentoIsoOrName.trim().toUpperCase(), dbId);
                                    regionByNameNormalized.put(normalizeName(departamentoIsoOrName), dbId);
                                }
                            }
                        }
                    }

                    Map<String,Object> row = new HashMap<>();
                    row.put("id_archivo", idArchivo);
                    row.put("id_region", idRegion); // puede ser null
                    row.put("household_code", safeTrim(rec.get("ID_Hogar")));
                    row.put("ingreso_categoria", safeTrimOrNull(rec.get("Ingreso_Categoria")));
                    row.put("razon_no_internet", safeTrimOrNull(rec.get("Razon_No_Internet")));
                    row.put("indice_habilidades", parseIntegerOrNull(rec.get("Indice_Habilidades_Digitales")));
                    row.put("tipo_uso_internet", safeTrimOrNull(rec.get("Tipo_Uso_Internet")));
                    row.put("calidad_servicio", safeTrimOrNull(rec.get("Calidad_Servicio_Percibida")));
                    row.put("n_personas", parseIntegerOrNull(rec.get("N_Personas")));
                    row.put("n_5_14", parseIntegerOrNull(rec.get("N_5_14")));
                    row.put("n_15_24", parseIntegerOrNull(rec.get("N_15_24")));
                    row.put("n_25_60", parseIntegerOrNull(rec.get("N_25_60")));
                    row.put("n_mayores_60", parseIntegerOrNull(rec.get("N_Mayores_60")));
                    row.put("n_mujeres", parseIntegerOrNull(rec.get("N_Mujeres")));
                    row.put("n_hombres", parseIntegerOrNull(rec.get("N_Hombres")));
                    row.put("barrera_costo", parseIntegerOrNull(rec.get("Barrera_Costo")));

                    buffer.add(row);

                    // si buffer alcanzó tamaño, ejecutar batch
                    if (buffer.size() >= BATCH_SIZE) {
                        insertedRows += executeBatchInsert(insertHogarSql, buffer);
                        buffer.clear();
                    }
                } catch (Exception e) {
                    skippedRows++;
                    log.warn("Error procesando fila {} : {}", totalRows, e.getMessage());
                    // registrar el error y continuar
                    // No agregamos stacktrace largo para no saturar logs; si necesitas, usa debug
                }
            }

            // insertar lo que quede en buffer
            if (!buffer.isEmpty()) {
                insertedRows += executeBatchInsert(insertHogarSql, buffer);
                buffer.clear();
            }

            result.totalRows = totalRows;
            result.insertedRows = insertedRows;
            result.skippedRows = skippedRows;

            log.info("Import CSV finalizado: totalRows={}, insertedRows={}, skippedRows={}, idArchivo={}", totalRows, insertedRows, skippedRows, idArchivo);

            // 4) Calcular y guardar agregados por región (UPSERT) para este archivo
            try {
                computeRegionAggregates(idArchivo);
                log.info("Aggregates calculados y almacenados para idArchivo={}", idArchivo);
            } catch (Exception e) {
                log.error("Error calculando region_aggregates para idArchivo={}: {}", idArchivo, e.getMessage());
                // no abortamos la importación por esto, pero lo registramos (si prefieres fallar, lanza excepción)
                result.errors.add("Error calculando agregados por región: " + e.getMessage());
            }

        } catch (IllegalArgumentException iae) {
            log.error("Validación CSV fallida: {}", iae.getMessage());
            throw iae;
        } catch (Exception e) {
            log.error("Error general importCsv: {}", e.getMessage(), e);
            throw e; // transaction rollback
        }

        return result;
    }

    private int executeBatchInsert(String insertSql, List<Map<String,Object>> rows) {
        if (rows.isEmpty()) return 0;
        int[] batchResult = jdbcTemplate.batchUpdate(insertSql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Map<String,Object> r = rows.get(i);
                // 1 id_archivo
                ps.setLong(1, (Long) r.get("id_archivo"));
                // 2 id_region
                if (r.get("id_region") != null) ps.setLong(2, (Long) r.get("id_region"));
                else ps.setNull(2, Types.BIGINT);
                // 3 household_code
                ps.setString(3, (String) r.get("household_code"));
                // 4 ingreso_categoria
                ps.setString(4, (String) r.get("ingreso_categoria"));
                // 5 razon_no_internet
                ps.setString(5, (String) r.get("razon_no_internet"));
                // 6 indice_habilidades
                if (r.get("indice_habilidades") != null) ps.setInt(6, (Integer) r.get("indice_habilidades"));
                else ps.setNull(6, Types.INTEGER);
                // 7 tipo_uso_internet
                ps.setString(7, (String) r.get("tipo_uso_internet"));
                // 8 calidad_servicio
                ps.setString(8, (String) r.get("calidad_servicio"));
                // 9..16 ints possibly null
                setIntOrNull(ps, 9, r.get("n_personas"));
                setIntOrNull(ps, 10, r.get("n_5_14"));
                setIntOrNull(ps, 11, r.get("n_15_24"));
                setIntOrNull(ps, 12, r.get("n_25_60"));
                setIntOrNull(ps, 13, r.get("n_mayores_60"));
                setIntOrNull(ps, 14, r.get("n_mujeres"));
                setIntOrNull(ps, 15, r.get("n_hombres"));
                setIntOrNull(ps, 16, r.get("barrera_costo"));
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
        int inserted = 0;
        for (int v : batchResult) if (v >= 0) inserted += v;
        return inserted;
    }

    private void setIntOrNull(PreparedStatement ps, int idx, Object value) throws SQLException {
        if (value == null) ps.setNull(idx, Types.INTEGER);
        else ps.setInt(idx, (Integer) value);
    }

    private Integer parseIntegerOrNull(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String safeTrim(String s) {
        return s == null ? null : s.trim();
    }

    private String safeTrimOrNull(String s) {
        String t = safeTrim(s);
        return (t == null || t.isEmpty()) ? null : t;
    }

    /**
     * Busqueda en BD por ISO o por nombre (normalizado). Retorna null si no encuentra.
     */
    private Long findRegionIdByIsoOrName(String isoOrName) {
        if (isoOrName == null) return null;
        try {
            String sqlIso = "SELECT id_region FROM regions WHERE UPPER(codigo_iso) = ? LIMIT 1";
            Long byIso = null;
            try {
                byIso = jdbcTemplate.queryForObject(sqlIso, new Object[]{isoOrName.trim().toUpperCase()}, Long.class);
            } catch (DataAccessException ex) {
                // ignore
            }
            if (byIso != null) return byIso;

            // intentar por nombre normalizado
            String normalized = normalizeName(isoOrName);
            String sqlName = "SELECT id_region FROM regions WHERE regexp_replace(upper(name_region), '[^A-Z0-9 ]', ' ', 'g') = ? LIMIT 1";
            try {
                return jdbcTemplate.queryForObject(sqlName, new Object[]{normalized}, Long.class);
            } catch (DataAccessException ex) {
                return null;
            }
        } catch (Exception e) {
            log.warn("Error findRegionIdByIsoOrName({}): {}", isoOrName, e.getMessage());
            return null;
        }
    }

    private static String normalizeName(String s) {
        if (s == null) return null;
        String n = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toUpperCase()
                .replaceAll("[^A-Z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return n;
    }

    /**
     * Calcula (UPSERT) agregados por región para el id_archivo dado.
     * Excluye hogares con id_region IS NULL (no mapeados).
     */
    private void computeRegionAggregates(Long idArchivo) {
        // SQL con ? repetidos donde haga falta
        String sql = ""
            + "INSERT INTO region_aggregates (id_archivo, id_region, total_hogares, avg_habilidades_digitales, "
            + "pct_barrera_costo, avg_personas_por_hogar, ingreso_modal, indice_desarrollo, computed_at) "
            + "SELECT "
            + "  h.id_archivo, "
            + "  h.id_region, "
            + "  COUNT(*) AS total_hogares, "
            + "  AVG(NULLIF(h.indice_habilidades,0))::double precision AS avg_habilidades_digitales, "
            + "  SUM(CASE WHEN h.barrera_costo > 0 THEN 1 ELSE 0 END)::double precision / NULLIF(COUNT(*),0) AS pct_barrera_costo, "
            + "  AVG(h.n_personas)::double precision AS avg_personas_por_hogar, "
            + "  (SELECT ingreso_categoria "
            + "     FROM hogares h2 "
            + "     WHERE h2.id_archivo = ? AND h2.id_region = h.id_region "
            + "     GROUP BY ingreso_categoria "
            + "     ORDER BY COUNT(*) DESC LIMIT 1) AS ingreso_modal, "
            + "  ( "
            + "    0.45 * (COALESCE(AVG(NULLIF(h.indice_habilidades,0)),0) / 5.0) "
            + "  + 0.35 * (1 - (SUM(CASE WHEN h.barrera_costo > 0 THEN 1 ELSE 0 END)::double precision / NULLIF(COUNT(*),1))) "
            + "  + 0.20 * ( "
            + "      CASE (SELECT ingreso_categoria "
            + "               FROM hogares h3 "
            + "               WHERE h3.id_archivo = ? AND h3.id_region = h.id_region "
            + "               GROUP BY ingreso_categoria "
            + "               ORDER BY COUNT(*) DESC LIMIT 1) "
            + "        WHEN 'Alto' THEN 1.0 "
            + "        WHEN 'Medio' THEN 0.6 "
            + "        WHEN 'Bajo' THEN 0.2 "
            + "        ELSE 0.4 "
            + "      END "
            + "    ) "
            + "  ) AS indice_desarrollo, "
            + "  NOW() AS computed_at "
            + "FROM hogares h "
            + "WHERE h.id_archivo = ? AND h.id_region IS NOT NULL "
            + "GROUP BY h.id_archivo, h.id_region "
            + "ON CONFLICT (id_archivo, id_region) DO UPDATE SET "
            + "  total_hogares = EXCLUDED.total_hogares, "
            + "  avg_habilidades_digitales = EXCLUDED.avg_habilidades_digitales, "
            + "  pct_barrera_costo = EXCLUDED.pct_barrera_costo, "
            + "  avg_personas_por_hogar = EXCLUDED.avg_personas_por_hogar, "
            + "  ingreso_modal = EXCLUDED.ingreso_modal, "
            + "  indice_desarrollo = EXCLUDED.indice_desarrollo, "
            + "  computed_at = EXCLUDED.computed_at;";

        // Repetimos idArchivo en los parámetros en el orden que aparecen en la query: ?, ?, ?
        jdbcTemplate.update(sql, idArchivo, idArchivo, idArchivo);
    }
}
