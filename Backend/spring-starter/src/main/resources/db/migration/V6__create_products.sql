CREATE TABLE products (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    nombre VARCHAR(160) NOT NULL,
    descripcion VARCHAR(1000) NULL,
    precio_referencia DECIMAL(15,2) NOT NULL,
    disponible BOOLEAN NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    destacado BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT ck_products_precio_referencia_positive CHECK (precio_referencia > 0),
    INDEX idx_products_category_id (category_id)
);
