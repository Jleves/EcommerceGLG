ALTER TABLE subcategories ADD COLUMN parent_subcategory_id BIGINT NULL;

ALTER TABLE subcategories
    ADD CONSTRAINT fk_subcategories_parent FOREIGN KEY (parent_subcategory_id) REFERENCES subcategories (id);

CREATE INDEX idx_subcategories_parent_id ON subcategories (parent_subcategory_id);

ALTER TABLE subcategories DROP INDEX uk_subcategories_category_name;

ALTER TABLE subcategories ADD COLUMN root_nombre_normalizado VARCHAR(160)
    GENERATED ALWAYS AS (CASE WHEN parent_subcategory_id IS NULL THEN nombre_normalizado ELSE NULL END);

ALTER TABLE subcategories ADD CONSTRAINT uk_subcategories_root_name
    UNIQUE (category_id, root_nombre_normalizado);

ALTER TABLE subcategories ADD CONSTRAINT uk_subcategories_sibling_name
    UNIQUE (category_id, parent_subcategory_id, nombre_normalizado);
