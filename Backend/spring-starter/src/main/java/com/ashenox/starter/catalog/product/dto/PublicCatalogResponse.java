package com.ashenox.starter.catalog.product.dto;

import com.ashenox.starter.catalog.category.dto.CategoryResponse;
import com.ashenox.starter.catalog.subcategory.dto.SubcategoryResponse;
import java.util.List;

public record PublicCatalogResponse(List<CategoryResponse> categories, List<SubcategoryResponse> subcategories,
                                    List<Facet> facets) {
    public record Facet(Long id, String name, List<String> values) {}
}
