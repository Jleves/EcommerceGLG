CREATE TABLE variant_offers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    variant_id BIGINT NOT NULL,
    precio_oferta DECIMAL(15,2) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_variant_offers_variant FOREIGN KEY (variant_id) REFERENCES product_variants (id),
    CONSTRAINT uk_variant_offers_variant UNIQUE (variant_id),
    CONSTRAINT ck_variant_offers_price_positive CHECK (precio_oferta > 0)
);
