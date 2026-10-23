package com.ashenox.starter.catalog.product.variant.controller;

import com.ashenox.starter.catalog.product.variant.dto.CreateVariantRequest;
import com.ashenox.starter.catalog.product.variant.dto.UpdateVariantRequest;
import com.ashenox.starter.catalog.product.variant.dto.ProductVariantResponse;
import com.ashenox.starter.catalog.product.variant.service.ProductVariantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/catalog/products/{productId}/variants")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','USER')")
public class ProductVariantController {
    private final ProductVariantService service;

    @GetMapping
    public List<ProductVariantResponse> list(@PathVariable Long productId) { return service.list(productId); }

    @PostMapping
    public ResponseEntity<ProductVariantResponse> create(@PathVariable Long productId,
            @Valid @RequestBody CreateVariantRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(productId, request));
    }

    @PutMapping("/{variantId}")
    public ProductVariantResponse update(@PathVariable Long productId, @PathVariable Long variantId,
            @Valid @RequestBody UpdateVariantRequest request) {
        return service.update(productId, variantId, request);
    }

    @PostMapping("/{variantId}/deactivate")
    public ProductVariantResponse deactivate(@PathVariable Long productId, @PathVariable Long variantId) {
        return service.setActive(productId, variantId, false);
    }

    @PostMapping("/{variantId}/reactivate")
    public ProductVariantResponse reactivate(@PathVariable Long productId, @PathVariable Long variantId) {
        return service.setActive(productId, variantId, true);
    }
}
