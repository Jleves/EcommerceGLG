CREATE TABLE subcategories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    category_id BIGINT NOT NULL,
    nombre VARCHAR(160) NOT NULL,
    nombre_normalizado VARCHAR(160) NOT NULL,
    descripcion VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_subcategories_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT uk_subcategories_category_name UNIQUE (category_id, nombre_normalizado),
    INDEX idx_subcategories_category_id (category_id)
);

ALTER TABLE products
    ADD COLUMN subcategory_id BIGINT NULL;

ALTER TABLE products
    ADD CONSTRAINT fk_products_subcategory FOREIGN KEY (subcategory_id) REFERENCES subcategories (id);

CREATE INDEX idx_products_subcategory_id ON products (subcategory_id);
