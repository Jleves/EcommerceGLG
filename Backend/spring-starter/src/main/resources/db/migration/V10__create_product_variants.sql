CREATE TABLE product_variants (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    sku VARCHAR(160) NULL,
    sku_normalizado VARCHAR(160) NULL,
    precio_referencia DECIMAL(15,2) NOT NULL,
    disponible BOOLEAN NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_product_variants_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT uk_product_variants_sku_normalizado UNIQUE (sku_normalizado),
    CONSTRAINT ck_product_variants_precio_positive CHECK (precio_referencia > 0),
    INDEX idx_product_variants_product_id (product_id)
);

CREATE TABLE variant_characteristics (
    variant_id BIGINT NOT NULL,
    characteristic_id BIGINT NOT NULL,
    valor VARCHAR(500) NOT NULL,
    valor_normalizado VARCHAR(500) NOT NULL,
    unidad VARCHAR(100) NULL,
    unidad_normalizada VARCHAR(100) NULL,
    PRIMARY KEY (variant_id),
    CONSTRAINT fk_variant_characteristics_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
    CONSTRAINT fk_variant_characteristics_characteristic FOREIGN KEY (characteristic_id) REFERENCES characteristics (id),
    INDEX idx_variant_characteristics_characteristic_id (characteristic_id)
);

INSERT INTO product_variants (product_id, precio_referencia, disponible, activo, created_at, updated_at)
SELECT id, precio_referencia, disponible, TRUE, created_at, updated_at FROM products;
