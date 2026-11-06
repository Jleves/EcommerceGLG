CREATE TABLE subscriptions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    nombre VARCHAR(160) NULL,
    email VARCHAR(254) NULL,
    whatsapp VARCHAR(16) NULL,
    consentimiento_email_en DATETIME(6) NULL,
    consentimiento_whatsapp_en DATETIME(6) NULL,
    baja_email_en DATETIME(6) NULL,
    baja_whatsapp_en DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_subscriptions_email UNIQUE (email),
    CONSTRAINT uk_subscriptions_whatsapp UNIQUE (whatsapp),
    CONSTRAINT chk_subscriptions_contact CHECK (email IS NOT NULL OR whatsapp IS NOT NULL)
);
