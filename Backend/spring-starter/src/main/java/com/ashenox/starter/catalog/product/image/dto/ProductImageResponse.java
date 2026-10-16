package com.ashenox.starter.catalog.product.image.dto;

public record ProductImageResponse(Long id, int position, boolean primary,
                                   String thumbnailUrl, String detailUrl) {
}
