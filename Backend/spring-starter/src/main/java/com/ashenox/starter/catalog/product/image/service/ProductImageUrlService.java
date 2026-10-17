package com.ashenox.starter.catalog.product.image.service;

import com.ashenox.starter.catalog.product.image.dto.ProductImageResponse;
import com.ashenox.starter.catalog.product.image.model.ProductImage;
import com.ashenox.starter.shared.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductImageUrlService {
    private final AppProperties properties;

    public String toCloudFrontUrl(String key) {
        return properties.getImages().getCloudFrontBaseUrl() + "/" + key;
    }

    public ProductImageResponse toResponse(ProductImage image) {
        return new ProductImageResponse(image.getId(), image.getPosition(), image.isPrimary(),
                toCloudFrontUrl(image.getThumbnailKey()), toCloudFrontUrl(image.getDetailKey()));
    }
}
