package com.fg.visualizationlogic.service;

import com.fg.visualizationlogic.dto.RegionDetailDto;
import com.fg.visualizationlogic.dto.RegionMetricsDto;
import com.fg.visualizationlogic.util.NameNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Service
public class MapDataService {

    private static final Logger log = LoggerFactory.getLogger(MapDataService.class);

    private final NamedParameterJdbcTemplate namedJdbc;

    public MapDataService(NamedParameterJdbcTemplate namedJdbc) {
        this.namedJdbc = namedJdbc;
    }

    /**
     * Obtiene métricas por región para el idArchivo y filtros dados.
     * Devuelve una lista con una fila por región (incluye regiones sin hogares con totalHogares=0).
     */
    public List<RegionMetricsDto> getMetricsByRegion(Long idArchivo, Map<String, String> rawFilters) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("idArchivo", idArchivo);

        // Construir WHERE dinámico (aplicable a la tabla hogares)
        String where = buildWhereClause(rawFilters, params);

        String sql = ""
                + "SELECT r.id_region, r.codigo_iso, r.name_region, "
                + "  COUNT(h.id_hogar) AS total_hogares, "
                + "  AVG(NULLIF(h.indice_habilidades,0))::double precision AS avg_habilidades_digitales, "
                + "  SUM(CASE WHEN h.barrera_costo > 0 THEN 1 ELSE 0 END)::double precision / NULLIF(COUNT(h.id_hogar),0) AS pct_barrera_costo, "
                + "  AVG(h.n_personas)::double precision AS avg_personas_por_hogar, "
                + "  (SELECT ingreso_categoria FROM hogares h2 WHERE h2.id_archivo = :idArchivo AND h2.id_region = r.id_region "
                + "     GROUP BY ingreso_categoria ORDER BY COUNT(*) DESC LIMIT 1) AS ingreso_modal "
                + "FROM regions r "
                + "LEFT JOIN hogares h ON h.id_region = r.id_region AND h.id_archivo = :idArchivo "
                + (where.isEmpty() ? " " : (" WHERE " + where + " "))
                + "GROUP BY r.id_region, r.codigo_iso, r.name_region "
                + "ORDER BY r.name_region;";

        List<RegionMetricsDto> list = namedJdbc.query(sql, params, (rs, rowNum) -> mapRowToRegionMetrics(rs));
        // compute indice_desarrollo locally if null
        list.forEach(this::ensureIndiceComputed);
        return list;
    }

    private RegionMetricsDto mapRowToRegionMetrics(ResultSet rs) throws SQLException {
        RegionMetricsDto d = new RegionMetricsDto();
        d.setIdRegion(rs.getLong("id_region"));
        d.setCodigoIso(rs.getString("codigo_iso"));
        d.setNameRegion(rs.getString("name_region"));
        d.setTotalHogares(rs.getLong("total_hogares"));

        double avgH = rs.getDouble("avg_habilidades_digitales");
        d.setAvgHabilidadesDigitales(rs.wasNull() ? null : avgH);

        double pctB = rs.getDouble("pct_barrera_costo");
        d.setPctBarreraCosto(rs.wasNull() ? null : pctB);

        double avgP = rs.getDouble("avg_personas_por_hogar");
        d.setAvgPersonasPorHogar(rs.wasNull() ? null : avgP);

        d.setIngresoModal(rs.getString("ingreso_modal"));

        // Intentar leer indice_desarrollo sólo si la columna existe en el ResultSet
        try {
            // rs.findColumn lanzará SQLException si no existe la columna
            rs.findColumn("indice_desarrollo");
            double idx = rs.getDouble("indice_desarrollo");
            d.setIndiceDesarrollo(rs.wasNull() ? null : idx);
        } catch (SQLException ex) {
            // columna no presente en el SELECT -> se calculará después si hace falta
        }

        return d;
    }


    private void ensureIndiceComputed(RegionMetricsDto d) {
        if (d.getIndiceDesarrollo() == null) {
            double sh = (d.getAvgHabilidadesDigitales() == null) ? 0.0 : d.getAvgHabilidadesDigitales() / 5.0;
            double sb = (d.getPctBarreraCosto() == null) ? 0.0 : (1.0 - d.getPctBarreraCosto());
            double si = mapIngresoToScore(d.getIngresoModal());
            double score = 0.45 * sh + 0.35 * sb + 0.20 * si;
            d.setIndiceDesarrollo(Math.round(score * 10000.0) / 10000.0);
        }
    }

    private double mapIngresoToScore(String ingreso) {
        if (ingreso == null) return 0.2;
        switch (ingreso.toLowerCase()) {
            case "alto": return 1.0;
            case "medio": return 0.6;
            case "bajo": return 0.2;
            default: return 0.4;
        }
    }

    /**
     * Devuelve detalle (desgloses) para una región con filtros aplicados.
     */
    public RegionDetailDto getRegionDetail(Long idArchivo, Long idRegion, Map<String, String> rawFilters) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("idArchivo", idArchivo);
        params.addValue("idRegion", idRegion);

        String where = buildWhereClause(rawFilters, params);
        // Añadir condición de id_region
        if (!where.isEmpty()) where = " ( " + where + " ) AND h.id_region = :idRegion ";
        else where = " h.id_region = :idRegion ";

        RegionDetailDto dto = new RegionDetailDto();
        dto.setIdRegion(idRegion);

        // 1) Aggregated metrics for that region
        String aggSql = ""
                + "SELECT COUNT(*) AS total_hogares, "
                + "AVG(NULLIF(h.indice_habilidades,0))::double precision AS avg_habilidades_digitales, "
                + "SUM(CASE WHEN h.barrera_costo > 0 THEN 1 ELSE 0 END)::double precision / NULLIF(COUNT(*),0) AS pct_barrera_costo "
                + "FROM hogares h WHERE h.id_archivo = :idArchivo AND " + where + ";";

        namedJdbc.query(aggSql, params, rs -> {
            long total = rs.getLong("total_hogares");
            dto.setTotalHogares(total);
            double avgH = rs.getDouble("avg_habilidades_digitales");
            dto.setAvgHabilidadesDigitales(rs.wasNull() ? null : avgH);
            double pctB = rs.getDouble("pct_barrera_costo");
            dto.setPctBarreraCosto(rs.wasNull() ? null : pctB);
        });

        // compute indice_development for this region (again using the same weights)
        if (dto.getTotalHogares() > 0) {
            double sh = (dto.getAvgHabilidadesDigitales() == null) ? 0.0 : dto.getAvgHabilidadesDigitales() / 5.0;
            double sb = (dto.getPctBarreraCosto() == null) ? 0.0 : (1.0 - dto.getPctBarreraCosto());
            // get ingreso modal for region with filters applied
            String incomeSql = "SELECT ingreso_categoria, COUNT(*) AS cnt FROM hogares h "
                    + "WHERE h.id_archivo = :idArchivo AND " + where + " GROUP BY ingreso_categoria ORDER BY cnt DESC LIMIT 1;";
            String ingresoModal = namedJdbc.query(incomeSql, params, rs -> {
                if (rs.next()) return rs.getString("ingreso_categoria");
                return null;
            });
            double si = mapIngresoToScore(ingresoModal);
            dto.setIndiceDesarrollo(Math.round((0.45*sh + 0.35*sb + 0.20*si)*10000.0)/10000.0);
        } else {
            dto.setIndiceDesarrollo(0.0);
        }

        // 2) Tipo de uso counts
        String tipoUsoSql = "SELECT tipo_uso_internet, COUNT(*) AS cnt FROM hogares h WHERE h.id_archivo = :idArchivo AND " + where
                + " GROUP BY tipo_uso_internet ORDER BY cnt DESC;";
        Map<String,Integer> tipoUso = new LinkedHashMap<>();
        namedJdbc.query(tipoUsoSql, params, rs -> {
            String key = rs.getString("tipo_uso_internet");
            int cnt = rs.getInt("cnt");
            if (key == null) key = "No Aplica";
            tipoUso.put(key, cnt);
        });
        dto.setTipoUsoCounts(tipoUso);

        // 3) Razon no internet - top 10
        String razonSql = "SELECT razon_no_internet, COUNT(*) AS cnt FROM hogares h WHERE h.id_archivo = :idArchivo AND " + where
                + " GROUP BY razon_no_internet ORDER BY cnt DESC LIMIT 10;";
        Map<String,Integer> razones = new LinkedHashMap<>();
        namedJdbc.query(razonSql, params, rs -> {
            String r = rs.getString("razon_no_internet");
            int cnt = rs.getInt("cnt");
            if (r == null) r = "No Aplica";
            razones.put(r, cnt);
        });
        dto.setRazonNoInternetTop(razones);

        // 4) Ingreso distribution
        String ingresoDistSql = "SELECT ingreso_categoria, COUNT(*) as cnt FROM hogares h WHERE h.id_archivo = :idArchivo AND " + where
                + " GROUP BY ingreso_categoria ORDER BY cnt DESC;";
        Map<String,Integer> ingresoDist = new LinkedHashMap<>();
        namedJdbc.query(ingresoDistSql, params, rs -> {
            String k = rs.getString("ingreso_categoria");
            int cnt = rs.getInt("cnt");
            if (k == null) k = "No Aplica";
            ingresoDist.put(k, cnt);
        });
        dto.setIngresoDistribution(ingresoDist);

        // 5) Edad groups counts
        String edadSql = "SELECT SUM(COALESCE(n_5_14,0)) as e1, SUM(COALESCE(n_15_24,0)) as e2, SUM(COALESCE(n_25_60,0)) as e3, SUM(COALESCE(n_mayores_60,0)) as e4 "
                + "FROM hogares h WHERE h.id_archivo = :idArchivo AND " + where + ";";
        namedJdbc.query(edadSql, params, rs -> {
            if (rs.next()) {
                Map<String,Integer> edades = new LinkedHashMap<>();
                edades.put("5-14", rs.getInt("e1"));
                edades.put("15-24", rs.getInt("e2"));
                edades.put("25-60", rs.getInt("e3"));
                edades.put("mayores_60", rs.getInt("e4"));
                dto.setEdadGroups(edades);
            }
        });

        // 6) Genero counts
        String generoSql = "SELECT SUM(COALESCE(n_mujeres,0)) as mujeres, SUM(COALESCE(n_hombres,0)) as hombres "
                + "FROM hogares h WHERE h.id_archivo = :idArchivo AND " + where + ";";
        namedJdbc.query(generoSql, params, rs -> {
            if (rs.next()) {
                Map<String,Integer> gen = new LinkedHashMap<>();
                gen.put("Mujer", rs.getInt("mujeres"));
                gen.put("Hombre", rs.getInt("hombres"));
                dto.setGeneroCounts(gen);
            }
        });

        return dto;
    }

    /**
     * Construye cláusula WHERE (sin la condición id_archivo) según filtros pasados en rawFilters.
     * Los filtros válidos:
     * - ingresoCategoria (string)
     * - tipoUso (string)
     * - calidadServicio (string)
     * - edad (5-14, 15-24, 25-60, mayores_60)
     * - genero (Mujer/Hombre)
     * - minIndiceHabilidades (int)
     * - onlyBarreraCosto (true/false)
     */
    private String buildWhereClause(Map<String, String> rawFilters, MapSqlParameterSource params) {
        List<String> clauses = new ArrayList<>();
        // always ensure id_archivo matches (we add externally where needed)
        clauses.add("h.id_archivo = :idArchivo");

        if (rawFilters == null || rawFilters.isEmpty()) {
            return String.join(" AND ", clauses);
        }

        String ingresoCategoria = rawFilters.get("ingresoCategoria");
        if (ingresoCategoria != null && !ingresoCategoria.isBlank()) {
            clauses.add("h.ingreso_categoria = :ingresoCategoria");
            params.addValue("ingresoCategoria", ingresoCategoria);
        }

        String tipoUso = rawFilters.get("tipoUso");
        if (tipoUso != null && !tipoUso.isBlank()) {
            clauses.add("h.tipo_uso_internet = :tipoUso");
            params.addValue("tipoUso", tipoUso);
        }

        String calidad = rawFilters.get("calidadServicio");
        if (calidad != null && !calidad.isBlank()) {
            clauses.add("h.calidad_servicio = :calidadServicio");
            params.addValue("calidadServicio", calidad);
        }

        String edad = rawFilters.get("edad");
        if (edad != null && !edad.isBlank()) {
            switch (edad) {
                case "5-14": clauses.add("h.n_5_14 > 0"); break;
                case "15-24": clauses.add("h.n_15_24 > 0"); break;
                case "25-60": clauses.add("h.n_25_60 > 0"); break;
                case "mayores_60": clauses.add("h.n_mayores_60 > 0"); break;
                default: break;
            }
        }

        String genero = rawFilters.get("genero");
        if (genero != null && !genero.isBlank()) {
            if (genero.equalsIgnoreCase("Mujer")) clauses.add("h.n_mujeres > 0");
            if (genero.equalsIgnoreCase("Hombre")) clauses.add("h.n_hombres > 0");
        }

        String minIdx = rawFilters.get("minIndiceHabilidades");
        if (minIdx != null) {
            try {
                int v = Integer.parseInt(minIdx);
                clauses.add("h.indice_habilidades >= :minIndiceHabilidades");
                params.addValue("minIndiceHabilidades", v);
            } catch (NumberFormatException ignore) { }
        }

        String onlyBarrera = rawFilters.get("onlyBarreraCosto");
        if ("true".equalsIgnoreCase(onlyBarrera)) {
            clauses.add("h.barrera_costo > 0");
        }

        // se devuelven todas las cláusulas concatenadas con AND
        return String.join(" AND ", clauses);
    }

    /**
     * Recalcula y guarda agregados por región para el idArchivo (UPSERT).
     * Igual a lo implementado en import service pero expuesto como operación manual.
     */
    public void computeRegionAggregates(Long idArchivo) {
        log.info("Computing region aggregates for idArchivo={}", idArchivo);
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
                + "     WHERE h2.id_archivo = :idArchivo AND h2.id_region = h.id_region "
                + "     GROUP BY ingreso_categoria "
                + "     ORDER BY COUNT(*) DESC LIMIT 1) AS ingreso_modal, "
                + "  ( "
                + "    0.45 * (COALESCE(AVG(NULLIF(h.indice_habilidades,0)),0) / 5.0) "
                + "  + 0.35 * (1 - (SUM(CASE WHEN h.barrera_costo > 0 THEN 1 ELSE 0 END)::double precision / NULLIF(COUNT(*),1))) "
                + "  + 0.20 * ( "
                + "      CASE (SELECT ingreso_categoria "
                + "               FROM hogares h3 "
                + "               WHERE h3.id_archivo = :idArchivo AND h3.id_region = h.id_region "
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
                + "WHERE h.id_archivo = :idArchivo AND h.id_region IS NOT NULL "
                + "GROUP BY h.id_archivo, h.id_region "
                + "ON CONFLICT (id_archivo, id_region) DO UPDATE SET "
                + "  total_hogares = EXCLUDED.total_hogares, "
                + "  avg_habilidades_digitales = EXCLUDED.avg_habilidades_digitales, "
                + "  pct_barrera_costo = EXCLUDED.pct_barrera_costo, "
                + "  avg_personas_por_hogar = EXCLUDED.avg_personas_por_hogar, "
                + "  ingreso_modal = EXCLUDED.ingreso_modal, "
                + "  indice_desarrollo = EXCLUDED.indice_desarrollo, "
                + "  computed_at = EXCLUDED.computed_at;";

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("idArchivo", idArchivo);
        namedJdbc.update(sql, params);
    }
}
