package com.ashenox.starter.catalog.product.offer.dto;

import java.util.List;

public record OfferPageResponse(List<OfferResponse> content, int page, int size,
                                long totalElements, int totalPages) {
}
