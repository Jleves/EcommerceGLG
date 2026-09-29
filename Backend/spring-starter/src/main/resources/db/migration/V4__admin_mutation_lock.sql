CREATE TABLE admin_mutation_lock (
    id INTEGER NOT NULL PRIMARY KEY,
    CONSTRAINT chk_admin_mutation_singleton CHECK (id = 1)
);

INSERT INTO admin_mutation_lock (id) VALUES (1);
