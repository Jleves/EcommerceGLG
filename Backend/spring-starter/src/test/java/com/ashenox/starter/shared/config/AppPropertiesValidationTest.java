package com.ashenox.starter.shared.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AppPropertiesValidationTest {

    private Validator validator;
    private AppProperties properties;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
        properties = validProperties();
    }

    @Test
    void rejectsShortJwtSecret() {
        properties.getSecurity().getJwt().setSecret("dG9vLXNob3J0");

        assertThat(validator.validate(properties))
                .anyMatch(violation -> violation.getPropertyPath().toString().contains("secretValid"));
    }

    @Test
    void requiresCredentialsWhenSuperAdminBootstrapIsEnabled() {
        properties.getInit().getSuperadmin().setEnabled(true);

        assertThat(validator.validate(properties))
                .anyMatch(violation -> violation.getPropertyPath().toString().contains("configurationValid"));
    }

    @Test
    void rejectsNonPositivePasswordResetTtl() {
        properties.getSecurity().getPasswordReset().setTokenTtl(Duration.ZERO);

        assertThat(validator.validate(properties))
                .anyMatch(violation -> violation.getPropertyPath().toString().contains("tokenTtlValid"));
    }

    @Test
    void rejectsMissingOrShortMfaHmacSecret() {
        properties.getSecurity().getMfa().setHmacSecret(null);
        assertThat(validator.validate(properties))
                .anyMatch(violation -> violation.getPropertyPath().toString().contains("hmacSecretValid"));

        properties.getSecurity().getMfa().setHmacSecret("dG9vLXNob3J0");
        assertThat(validator.validate(properties))
                .anyMatch(violation -> violation.getPropertyPath().toString().contains("hmacSecretValid"));
    }

    @Test
    void rejectsReusingJwtKeyForMfa() {
        properties.getSecurity().getMfa().setHmacSecret(properties.getSecurity().getJwt().getSecret());
        assertThat(validator.validate(properties))
                .anyMatch(violation -> violation.getPropertyPath().toString().contains("mfaKeyIndependent"));
    }

    @Test
    void rejectsInvalidMfaDurationsAndLimits() {
        properties.getSecurity().getMfa().setChallengeTtl(Duration.ZERO);
        properties.getSecurity().getMfa().setMaxCodeIssuesPerWindow(0);

        assertThat(validator.validate(properties))
                .anyMatch(violation -> violation.getPropertyPath().toString().contains("durationConfigurationValid"));
        assertThat(validator.validate(properties))
                .anyMatch(violation -> violation.getPropertyPath().toString().contains("maxCodeIssuesPerWindow"));
    }

    @Test
    void rejectsMissingImageBucketAndCloudFrontBaseUrl() {
        properties.getImages().setBucket(null);
        properties.getImages().setCloudFrontBaseUrl(null);

        assertThat(validator.validate(properties))
                .extracting(violation -> violation.getMessage())
                .contains("app.images.bucket es obligatorio", "app.images.cloud-front-base-url es obligatoria");
    }

    @Test
    void rejectsInvalidImageRegionUrlLimitsAndTtl() {
        properties.getImages().setRegion("eu-west-1");
        properties.getImages().setCloudFrontBaseUrl(URI.create("http://example.com/path"));
        properties.getImages().setMaxFileSizeBytes(0);
        properties.getImages().setMaxPixels(0);
        properties.getImages().setThumbnailMaxWidth(0);
        properties.getImages().setDetailMaxWidth(0);
        properties.getImages().setCacheTtl(Duration.ZERO);

        assertThat(validator.validate(properties))
                .extracting(violation -> violation.getMessage())
                .contains("app.images.region debe ser us-east-1",
                        "app.images.cloud-front-base-url debe ser una URL HTTPS sin ruta, consulta ni fragmento",
                        "app.images.max-file-size-bytes debe ser mayor que cero",
                        "app.images.max-pixels debe ser mayor que cero",
                        "app.images.thumbnail-max-width debe ser mayor que cero",
                        "app.images.detail-max-width debe ser mayor que cero",
                        "app.images.cache-ttl debe ser mayor que cero");
    }

    private AppProperties validProperties() {
        AppProperties result = new AppProperties();
        result.getSecurity().getJwt().setSecret("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=");
        result.getSecurity().getMfa().setHmacSecret("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
        result.getMail().setHost("localhost");
        result.getMail().setPort(1025);
        result.getMail().setUsername("test@example.com");
        result.getMail().setPassword("test-password");
        result.getMail().setVerificationBaseUrl(URI.create("http://localhost:8080"));
        result.getMail().setFrontendBaseUrl(URI.create("http://localhost:5173"));
        result.getImages().setBucket("test-product-images-staging");
        result.getImages().setRegion("us-east-1");
        result.getImages().setCloudFrontBaseUrl(URI.create("https://test-images.cloudfront.net"));
        return result;
    }
}
