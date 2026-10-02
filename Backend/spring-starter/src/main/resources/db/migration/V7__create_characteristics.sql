CREATE TABLE characteristics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nombre VARCHAR(160) NOT NULL,
    nombre_normalizado VARCHAR(160) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_characteristics_nombre_normalizado UNIQUE (nombre_normalizado)
);

CREATE TABLE product_characteristics (
    product_id BIGINT NOT NULL,
    characteristic_id BIGINT NOT NULL,
    valor VARCHAR(500) NOT NULL,
    unidad VARCHAR(100) NULL,
    PRIMARY KEY (product_id, characteristic_id),
    CONSTRAINT fk_product_characteristics_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT fk_product_characteristics_characteristic FOREIGN KEY (characteristic_id) REFERENCES characteristics (id),
    INDEX idx_product_characteristics_characteristic_id (characteristic_id)
);
