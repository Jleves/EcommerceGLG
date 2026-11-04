package com.ashenox.starter.catalog.product.controller;

import com.ashenox.starter.catalog.product.dto.PublicProductDetailResponse;
import com.ashenox.starter.catalog.product.dto.PublicProductPageResponse;
import com.ashenox.starter.catalog.product.service.PublicProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/products")
@RequiredArgsConstructor
public class PublicProductController {
    private final PublicProductService service;

    @GetMapping
    public PublicProductPageResponse list(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size,
                                          @RequestParam(required = false) String q,
                                          @RequestParam(required = false) Long categoriaId,
                                          @RequestParam(required = false) Long subcategoriaId,
                                          @RequestParam(required = false) String marca,
                                          @RequestParam(name = "atributo", required = false) List<String> atributos) {
        return service.list(page, size, q, categoriaId, subcategoriaId, marca,
                atributos == null ? List.of() : atributos);
    }

    @GetMapping("/{id}")
    public PublicProductDetailResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @GetMapping("/{id}/related")
    public PublicProductPageResponse related(@PathVariable Long id) {
        return service.related(id);
    }
}
