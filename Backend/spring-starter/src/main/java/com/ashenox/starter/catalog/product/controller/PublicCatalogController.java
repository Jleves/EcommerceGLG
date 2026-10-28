package com.ashenox.starter.catalog.product.controller;

import com.ashenox.starter.catalog.category.dto.CategoryResponse;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.characteristic.repository.ProductCharacteristicRepository;
import com.ashenox.starter.catalog.product.dto.PublicCatalogResponse;
import com.ashenox.starter.catalog.product.variant.repository.VariantCharacteristicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/public/catalog")
@RequiredArgsConstructor
public class PublicCatalogController {
    private final CategoryRepository categories;
    private final ProductCharacteristicRepository characteristics;
    private final VariantCharacteristicRepository variantCharacteristics;

    @GetMapping
    @Transactional(readOnly = true)
    public PublicCatalogResponse get() {
        List<CategoryResponse> publicCategories = categories.findByActivoTrueOrderByNombreAscIdAsc()
                .stream().map(CategoryResponse::from).toList();
        Map<Long, FacetBuilder> byId = new LinkedHashMap<>();
        List<Object[]> values = new ArrayList<>(characteristics.findPublicFacetValues());
        values.addAll(variantCharacteristics.findPublicFacetValues());
        for (Object[] row : values) {
            Long id = (Long) row[0];
            String name = (String) row[1];
            String value = ((String) row[2]).strip();
            if (!value.isEmpty()) byId.computeIfAbsent(id, ignored -> new FacetBuilder(id, name)).values.add(value);
        }
        List<PublicCatalogResponse.Facet> facets = byId.values().stream()
                .sorted(Comparator.comparing(facet -> facet.name, String.CASE_INSENSITIVE_ORDER))
                .map(facet -> new PublicCatalogResponse.Facet(facet.id, facet.name,
                        facet.values.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList()))
                .toList();
        return new PublicCatalogResponse(publicCategories, facets);
    }

    private static class FacetBuilder {
        private final Long id;
        private final String name;
        private final List<String> values = new ArrayList<>();

        private FacetBuilder(Long id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
