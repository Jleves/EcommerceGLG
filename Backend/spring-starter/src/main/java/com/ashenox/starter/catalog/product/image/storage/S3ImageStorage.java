package com.ashenox.starter.catalog.product.image.storage;

import com.ashenox.starter.shared.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
@RequiredArgsConstructor
public class S3ImageStorage {
    private final S3Client s3;
    private final AppProperties properties;

    public void put(String key, byte[] webp) {
        s3.putObject(PutObjectRequest.builder()
                .bucket(properties.getImages().getBucket())
                .key(key)
                .contentType("image/webp")
                .cacheControl("public, max-age=" + properties.getImages().getCacheTtl().toSeconds())
                .build(), RequestBody.fromBytes(webp));
    }

    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.getImages().getBucket()).key(key).build());
    }
}
