ALTER TABLE products DROP CONSTRAINT ck_products_precio_referencia_positive;
ALTER TABLE products DROP COLUMN precio_referencia;
ALTER TABLE products DROP COLUMN disponible;
