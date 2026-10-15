package com.ashenox.starter.catalog.product.image.config;

import com.ashenox.starter.shared.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;

class ProductImageConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "app.security.jwt.secret=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
                    "app.security.mfa.hmac-secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                    "app.mail.host=localhost",
                    "app.mail.username=test",
                    "app.mail.password=test-password",
                    "app.mail.verification-base-url=http://localhost:8080",
                    "app.mail.frontend-base-url=http://localhost:5173",
                    "app.images.region=us-east-1");

    @Test
    void startupFailsClearlyWithoutBucket() {
        contextRunner.withPropertyValues("app.images.cloud-front-base-url=https://staging.cloudfront.net")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()).getMessage())
                            .contains("app.images.bucket es obligatorio");
                });
    }

    @Test
    void startupFailsClearlyWithoutCloudFrontDomain() {
        contextRunner.withPropertyValues("app.images.bucket=glgcorralon-product-images-staging-121026")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()).getMessage())
                            .contains("app.images.cloud-front-base-url");
                });
    }

    @Test
    void stagingConfigurationUsesOnlyItsBucketAndRegion() {
        contextRunner.withPropertyValues(
                        "app.images.bucket=glgcorralon-product-images-staging-121026",
                        "app.images.cloud-front-base-url=https://d296ipo7jtz4ug.cloudfront.net")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AppProperties.Images images = context.getBean(AppProperties.class).getImages();
                    assertThat(images.getBucket()).isEqualTo("glgcorralon-product-images-staging-121026");
                    assertThat(images.getCloudFrontBaseUrl().toString())
                            .isEqualTo("https://d296ipo7jtz4ug.cloudfront.net");
                    assertThat(context.getBean(S3Client.class).serviceClientConfiguration().region())
                            .isEqualTo(Region.US_EAST_1);
                });
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppProperties.class)
    @Import(ProductImageS3Config.class)
    static class TestConfiguration {
    }
}
