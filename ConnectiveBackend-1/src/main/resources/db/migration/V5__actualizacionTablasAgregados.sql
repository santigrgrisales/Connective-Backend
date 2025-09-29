-- V5__actualizacionTablasAgregados.sql
-- Actualiza region_aggregates y crea tabla auxiliar para mapeos de geojson

-- 1) Asegurar existencia de tabla region_aggregates (si no existe)
CREATE TABLE IF NOT EXISTS region_aggregates (
    id BIGSERIAL PRIMARY KEY,
    id_archivo BIGINT REFERENCES archivos(id_archivo) ON DELETE CASCADE,
    id_region BIGINT REFERENCES regions(id_region) ON DELETE CASCADE,
    total_hogares INT,
    avg_habilidades_digitales DOUBLE PRECISION,
    pct_barrera_costo DOUBLE PRECISION,
    avg_personas_por_hogar DOUBLE PRECISION,
    ingreso_modal VARCHAR(50),
    indice_desarrollo DOUBLE PRECISION,
    computed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(id_archivo, id_region)
);

-- 2) Añadir columnas para firma/filters (si no existen) para permitir cacheo futuro por filtros
ALTER TABLE region_aggregates
    ADD COLUMN IF NOT EXISTS filters_signature VARCHAR(255),
    ADD COLUMN IF NOT EXISTS filters JSONB;

-- 3) Índice para consultas por archivo y firma de filtros (acelerar búsquedas)
CREATE INDEX IF NOT EXISTS idx_region_aggregates_file_filters ON region_aggregates(id_archivo, filters_signature);

-- 4) Tabla auxiliar para mapear nombres de features en GeoJSON a id_region (útil para correcciones manuales)
CREATE TABLE IF NOT EXISTS geojson_region_map (
    id BIGSERIAL PRIMARY KEY,
    feature_name VARCHAR(255) NOT NULL UNIQUE,
    normalized_name VARCHAR(255) NOT NULL,
    id_region BIGINT REFERENCES regions(id_region),
    source VARCHAR(200),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 5) Índices de ayuda
CREATE INDEX IF NOT EXISTS idx_geojson_region_map_normalized ON geojson_region_map(normalized_name);
CREATE INDEX IF NOT EXISTS idx_region_aggregates_archivo ON region_aggregates(id_archivo);

-- Fin del script
