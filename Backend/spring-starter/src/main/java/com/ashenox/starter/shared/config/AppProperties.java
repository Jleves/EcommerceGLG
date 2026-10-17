package com.ashenox.starter.shared.config;

import io.jsonwebtoken.io.Decoders;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.security.MessageDigest;

@Component
@Validated
@Getter
@Setter
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    @Valid
    private Security security = new Security();

    @Valid
    private Mail mail = new Mail();

    @Valid
    private Init init = new Init();

    @Valid
    private Images images = new Images();

    @Getter
    @Setter
    public static class Images {
        @NotBlank(message = "app.images.bucket es obligatorio")
        private String bucket;

        @NotBlank(message = "app.images.region es obligatoria")
        private String region;

        @NotNull(message = "app.images.cloud-front-base-url es obligatoria")
        private URI cloudFrontBaseUrl;

        @Min(value = 1, message = "app.images.max-file-size-bytes debe ser mayor que cero")
        private long maxFileSizeBytes = 10L * 1024 * 1024;

        @Min(value = 1, message = "app.images.max-pixels debe ser mayor que cero")
        private long maxPixels = 20_000_000;

        @Min(value = 1, message = "app.images.thumbnail-max-width debe ser mayor que cero")
        private int thumbnailMaxWidth = 400;

        @Min(value = 1, message = "app.images.detail-max-width debe ser mayor que cero")
        private int detailMaxWidth = 1200;

        @NotNull(message = "app.images.cache-ttl es obligatorio")
        private Duration cacheTtl = Duration.ofHours(24);

        @Min(1)
        private int cleanupBatchSize = 50;

        @NotNull
        private Duration cleanupRetryBase = Duration.ofMinutes(1);

        @NotNull
        private Duration cleanupRetryMax = Duration.ofHours(1);

        @NotNull
        private Duration abandonedUploadGrace = Duration.ofHours(1);

        @AssertTrue(message = "app.images.region debe ser us-east-1")
        public boolean isRegionValid() {
            return region != null && region.equals("us-east-1");
        }

        @AssertTrue(message = "app.images.cloud-front-base-url debe ser una URL HTTPS sin ruta, consulta ni fragmento")
        public boolean isCloudFrontBaseUrlValid() {
            return cloudFrontBaseUrl != null
                    && "https".equalsIgnoreCase(cloudFrontBaseUrl.getScheme())
                    && cloudFrontBaseUrl.getHost() != null
                    && cloudFrontBaseUrl.getUserInfo() == null
                    && cloudFrontBaseUrl.getPort() == -1
                    && (cloudFrontBaseUrl.getPath() == null || cloudFrontBaseUrl.getPath().isEmpty())
                    && cloudFrontBaseUrl.getQuery() == null
                    && cloudFrontBaseUrl.getFragment() == null;
        }

        @AssertTrue(message = "app.images.cache-ttl debe ser mayor que cero")
        public boolean isCacheTtlValid() {
            return cacheTtl != null && !cacheTtl.isZero() && !cacheTtl.isNegative();
        }

        @AssertTrue(message = "Los intervalos de limpieza deben ser positivos; la espera de carga debe ser al menos 10 minutos")
        public boolean isCleanupTimingValid() {
            return cacheTtlValidDuration(cleanupRetryBase) && cacheTtlValidDuration(cleanupRetryMax)
                    && cacheTtlValidDuration(abandonedUploadGrace)
                    && cleanupRetryMax.compareTo(cleanupRetryBase) >= 0
                    && abandonedUploadGrace.compareTo(Duration.ofMinutes(10)) >= 0;
        }

        private boolean cacheTtlValidDuration(Duration duration) {
            return duration != null && !duration.isZero() && !duration.isNegative();
        }
    }

    @Getter
    @Setter
    public static class Security {
        @Valid
        private Jwt jwt = new Jwt();

        @Valid
        private Cookies cookies = new Cookies();

        @Valid
        private PasswordReset passwordReset = new PasswordReset();

        @Valid
        private Mfa mfa = new Mfa();

        @AssertTrue(message = "la clave HMAC MFA debe ser distinta de la clave JWT")
        public boolean isMfaKeyIndependent() {
            if (jwt.secret == null || jwt.secret.isBlank() || mfa.hmacSecret == null || mfa.hmacSecret.isBlank()) {
                return true;
            }
            try {
                return !MessageDigest.isEqual(Decoders.BASE64.decode(jwt.secret),
                        Base64.getDecoder().decode(mfa.hmacSecret));
            } catch (RuntimeException exception) {
                return true;
            }
        }
    }

    @Getter
    @Setter
    public static class PasswordReset {
        @NotNull
        private Duration tokenTtl = Duration.ofMinutes(30);

        @AssertTrue(message = "el TTL del token de recuperación debe ser mayor que cero")
        public boolean isTokenTtlValid() {
            return tokenTtl != null && !tokenTtl.isZero() && !tokenTtl.isNegative();
        }
    }

    @Getter
    @Setter
    public static class Mfa {
        @NotBlank
        private String hmacSecret;

        @NotNull
        private Duration challengeTtl = Duration.ofMinutes(5);

        @Min(1)
        private int maxFailedAttempts = 5;

        @NotNull
        private Duration resendCooldown = Duration.ofSeconds(60);

        @Min(1)
        private int maxResends = 3;

        @NotNull
        private Duration aggregateWindow = Duration.ofMinutes(15);

        @Min(1)
        private int maxCodeIssuesPerWindow = 5;

        @Min(1)
        private int maxCodeFailuresPerWindow = 10;

        @NotNull
        private Duration loginWindow = Duration.ofMinutes(15);

        @Min(1)
        private int maxLoginAttemptsPerAccount = 10;

        @Min(1)
        private int maxLoginAttemptsPerIp = 30;

        private List<String> trustedProxyAddresses = List.of();

        @AssertTrue(message = "app.security.mfa.hmac-secret debe ser Base64 y contener al menos 256 bits")
        public boolean isHmacSecretValid() {
            if (hmacSecret == null || hmacSecret.isBlank()) {
                return false;
            }
            try {
                return Base64.getDecoder().decode(hmacSecret).length >= 32;
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }

        @AssertTrue(message = "las duraciones de MFA deben ser mayores que cero")
        public boolean isDurationConfigurationValid() {
            return positive(challengeTtl) && positive(resendCooldown)
                    && positive(aggregateWindow) && positive(loginWindow);
        }

        private boolean positive(Duration duration) {
            return duration != null && !duration.isZero() && !duration.isNegative();
        }
    }

    @Getter
    @Setter
    public static class Cookies {
        private boolean secure = true;
    }

    @Getter
    @Setter
    public static class Jwt {
        @NotBlank
        private String secret;

        @NotNull
        private Duration accessExpiration = Duration.ofMinutes(15);

        @NotNull
        private Duration refreshExpiration = Duration.ofHours(3);

        @AssertTrue(message = "las duraciones de JWT deben ser mayores que cero")
        public boolean isExpirationConfigurationValid() {
            return accessExpiration != null && !accessExpiration.isZero() && !accessExpiration.isNegative()
                    && refreshExpiration != null && !refreshExpiration.isZero() && !refreshExpiration.isNegative();
        }

        @AssertTrue(message = "app.security.jwt.secret debe ser Base64 y contener al menos 256 bits")
        public boolean isSecretValid() {
            if (secret == null || secret.isBlank()) {
                return true;
            }
            try {
                return Decoders.BASE64.decode(secret).length >= 32;
            } catch (RuntimeException exception) {
                return false;
            }
        }
    }

    @Getter
    @Setter
    public static class Mail {
        @NotBlank
        private String host;

        @Min(1)
        @Max(65_535)
        private int port = 587;

        @NotBlank
        private String username;

        @NotBlank
        private String password;

        @NotNull
        private URI verificationBaseUrl;

        @NotNull
        private URI frontendBaseUrl;
    }

    @Getter
    @Setter
    public static class Init {
        @Valid
        private SuperAdmin superadmin = new SuperAdmin();
    }

    @Getter
    @Setter
    public static class SuperAdmin {
        private boolean enabled;

        @Email
        private String email;

        private String password;

        @AssertTrue(message = "email y password son obligatorios cuando el bootstrap de SUPER_ADMIN está habilitado")
        public boolean isConfigurationValid() {
            if (!enabled) {
                return true;
            }
            return email != null && !email.isBlank()
                    && password != null && password.length() >= 12 && password.length() <= 72;
        }
    }
}
