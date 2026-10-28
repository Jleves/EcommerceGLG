package com.ashenox.starter.catalog.product.dto;

import com.ashenox.starter.catalog.category.dto.CategoryResponse;
import java.util.List;

public record PublicCatalogResponse(List<CategoryResponse> categories, List<Facet> facets) {
    public record Facet(Long id, String name, List<String> values) {}
}
