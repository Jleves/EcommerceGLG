CREATE TABLE categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nombre VARCHAR(160) NOT NULL,
    nombre_normalizado VARCHAR(160) NOT NULL,
    descripcion VARCHAR(1000) NULL,
    icono VARCHAR(100) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_categories_nombre_normalizado UNIQUE (nombre_normalizado)
);
