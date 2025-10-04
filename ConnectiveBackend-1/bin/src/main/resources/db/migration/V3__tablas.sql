-- Tabla para regiones (nueva)
CREATE TABLE regions (
    id_region BIGSERIAL PRIMARY KEY,
    name_region VARCHAR(200) NOT NULL,
    codigo_iso VARCHAR(10)
);

-- Tabla para archivos/datasets (modificada)
CREATE TABLE archivos (
    id_archivo BIGSERIAL PRIMARY KEY,
    id_user BIGINT REFERENCES users(id) ON DELETE CASCADE,
    nombre_archivo VARCHAR(500) NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Tabla principal de hogares (modificada sustancialmente)
CREATE TABLE hogares (
    id_hogar BIGSERIAL PRIMARY KEY,
    id_archivo BIGINT REFERENCES archivos(id_archivo) ON DELETE CASCADE,
    id_region BIGINT REFERENCES regions(id_region) ON DELETE SET NULL,
    household_code VARCHAR(50),
    ingreso_categoria VARCHAR(50),
    razon_no_internet VARCHAR(200),
    indice_habilidades INT,
    tipo_uso_internet VARCHAR(100),
    calidad_servicio VARCHAR(50),
    n_personas INT,
    n_5_14 INT,
    n_15_24 INT,
    n_25_60 INT,
    n_mayores_60 INT,
    n_mujeres INT,
    n_hombres INT,
    barrera_costo INT DEFAULT 0
);

-- Índices para optimización
CREATE INDEX idx_hogares_archivo ON hogares(id_archivo);
CREATE INDEX idx_hogares_region ON hogares(id_region);
CREATE INDEX idx_hogares_ingreso ON hogares(ingreso_categoria);


-- Tabla opcional para agregados por región (modificada)
CREATE TABLE region_aggregates (
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