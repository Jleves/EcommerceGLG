package com.ashenox.starter.catalog.product.image.config;

import com.ashenox.starter.shared.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class ProductImageS3Config {

    @Bean
    S3Client productImageS3Client(AppProperties properties) {
        return S3Client.builder()
                .region(Region.of(properties.getImages().getRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
