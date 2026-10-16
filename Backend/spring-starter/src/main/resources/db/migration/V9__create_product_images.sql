CREATE TABLE product_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    position INT NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    thumbnail_key VARCHAR(500) NULL,
    detail_key VARCHAR(500) NULL,
    revision VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL,
    primary_product_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status = 'PUBLISHED' AND is_primary = TRUE THEN product_id ELSE NULL END
    ),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT uq_product_images_primary UNIQUE (primary_product_id),
    CONSTRAINT ck_product_images_status CHECK (status IN ('PENDING', 'PUBLISHED')),
    INDEX idx_product_images_product_status_position (product_id, status, position, id)
);

CREATE TABLE image_storage_operations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    product_id BIGINT NOT NULL,
    image_id BIGINT NOT NULL,
    thumbnail_key VARCHAR(500) NOT NULL,
    detail_key VARCHAR(500) NOT NULL,
    status VARCHAR(24) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_execution_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_image_storage_operations_product FOREIGN KEY (product_id) REFERENCES products (id),
    CONSTRAINT ck_image_storage_operations_status CHECK (status IN ('PENDING_UPLOAD', 'COMPLETE', 'CLEANUP_REQUIRED')),
    INDEX idx_image_storage_operations_due (status, next_execution_at, id),
    INDEX idx_image_storage_operations_image (image_id)
);
