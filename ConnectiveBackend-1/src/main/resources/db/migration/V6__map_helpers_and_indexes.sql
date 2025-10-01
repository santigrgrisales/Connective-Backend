-- V6__map_helpers_and_indexes.sql

-- Índices recomendados para acelerar filtros y agregaciones
CREATE INDEX IF NOT EXISTS idx_hogares_archivo ON hogares(id_archivo);
CREATE INDEX IF NOT EXISTS idx_hogares_region ON hogares(id_region);
CREATE INDEX IF NOT EXISTS idx_hogares_ingreso_categoria ON hogares(ingreso_categoria);
CREATE INDEX IF NOT EXISTS idx_hogares_tipo_uso ON hogares(tipo_uso_internet);
CREATE INDEX IF NOT EXISTS idx_hogares_calidad ON hogares(calidad_servicio);
CREATE INDEX IF NOT EXISTS idx_hogares_indice_habilidades ON hogares(indice_habilidades);
CREATE INDEX IF NOT EXISTS idx_hogares_n_15_24 ON hogares(n_15_24);

-- Vista opcional para métricas por region por archivo (útil para debugging o consultas ad-hoc)
CREATE OR REPLACE VIEW vw_region_metrics AS
SELECT
  h.id_archivo,
  r.id_region,
  r.codigo_iso,
  r.name_region,
  COUNT(h.id_hogar) AS total_hogares,
  AVG(NULLIF(h.indice_habilidades,0))::double precision AS avg_habilidades_digitales,
  SUM(CASE WHEN h.barrera_costo > 0 THEN 1 ELSE 0 END)::double precision / NULLIF(COUNT(h.id_hogar),0) AS pct_barrera_costo,
  AVG(h.n_personas)::double precision AS avg_personas_por_hogar
FROM regions r
LEFT JOIN hogares h ON h.id_region = r.id_region
GROUP BY h.id_archivo, r.id_region, r.codigo_iso, r.name_region;
