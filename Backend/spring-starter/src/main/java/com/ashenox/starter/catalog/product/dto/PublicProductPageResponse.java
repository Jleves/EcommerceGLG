package com.ashenox.starter.catalog.product.dto;

import java.util.List;

public record PublicProductPageResponse(List<PublicProductSummaryResponse> content, int page, int size,
                                        long totalElements, int totalPages) {
}
