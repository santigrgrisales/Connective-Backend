package com.fg.importcsv.service;



import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
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
import java.util.*;

@Service
public class CsvImportService {

    private final JdbcTemplate jdbcTemplate;

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
        if (key == null) throw new IllegalStateException("No se pudo generar id_archivo");
        long idArchivo = key.longValue();
        result.idArchivo = idArchivo;

        // 2) Cache de regions: codigo_iso -> id_region
        Map<String, Long> regionCache = new HashMap<>();
        jdbcTemplate.query("SELECT id_region, codigo_iso FROM regions",
                rs -> {
                    String iso = rs.getString("codigo_iso");
                    Long id = rs.getLong("id_region");
                    if (iso != null) regionCache.put(iso.trim().toUpperCase(), id);
                });

        // 3) Parse CSV
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

            List<CSVRecord> records = parser.getRecords();
            result.totalRows = records.size();
            if (records.isEmpty()) return result;

            // 4) Preparar batch insert
            final String insertHogarSql =
                    "INSERT INTO hogares (" +
                            "id_archivo, id_region, household_code, ingreso_categoria, razon_no_internet, indice_habilidades, " +
                            "tipo_uso_internet, calidad_servicio, n_personas, n_5_14, n_15_24, n_25_60, n_mayores_60, n_mujeres, n_hombres, barrera_costo" +
                            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

            // convert CSVRecords -> rowsToInsert
            List<Map<String,Object>> rowsToInsert = new ArrayList<>();
            int rowNum = 0;
            for (CSVRecord rec : records) {
                rowNum++;
                try {
                    String codigoIso = safeTrim(rec.get("Departamento_ISO"));
                    Long idRegion = null;
                    if (codigoIso != null && !codigoIso.isEmpty()) {
                        idRegion = regionCache.get(codigoIso.trim().toUpperCase());
                        // si no existe en cache, intentar buscar en BD y cachear
                        if (idRegion == null) {
                            Long dbId = findRegionIdByIso(codigoIso);
                            if (dbId != null) {
                                idRegion = dbId;
                                regionCache.put(codigoIso.trim().toUpperCase(), dbId);
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

                    rowsToInsert.add(row);
                } catch (Exception e) {
                    result.skippedRows++;
                    result.errors.add("Fila " + rowNum + ": " + e.getMessage());
                }
            }

            // batch insert (JdbcTemplate)
            try {
                int[] batchResult = jdbcTemplate.batchUpdate(insertHogarSql, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int i) throws SQLException {
                        Map<String,Object> r = rowsToInsert.get(i);
                        // id_archivo
                        ps.setLong(1, (Long) r.get("id_archivo"));
                        // id_region
                        if (r.get("id_region") != null) ps.setLong(2, (Long) r.get("id_region"));
                        else ps.setNull(2, Types.BIGINT);
                        ps.setString(3, (String) r.get("household_code"));
                        ps.setString(4, (String) r.get("ingreso_categoria"));
                        ps.setString(5, (String) r.get("razon_no_internet"));
                        if (r.get("indice_habilidades") != null) ps.setInt(6, (Integer) r.get("indice_habilidades"));
                        else ps.setNull(6, Types.INTEGER);
                        ps.setString(7, (String) r.get("tipo_uso_internet"));
                        ps.setString(8, (String) r.get("calidad_servicio"));
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
                        return rowsToInsert.size();
                    }
                });
                // calcular insertedRows (sum de batchResult)
                int inserted = 0;
                for (int v : batchResult) if (v >= 0) inserted += v;
                result.insertedRows = inserted;
            } catch (DataAccessException dae) {
                throw dae; // con @Transactional se hará rollback y el registro en archivos también
            }
        }

        return result;
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

    private Long findRegionIdByIso(String iso) {
        try {
            String sql = "SELECT id_region FROM regions WHERE UPPER(codigo_iso) = ?";
            return jdbcTemplate.queryForObject(sql, new Object[]{iso.trim().toUpperCase()}, Long.class);
        } catch (DataAccessException ex) {
            return null;
        }
    }
}

