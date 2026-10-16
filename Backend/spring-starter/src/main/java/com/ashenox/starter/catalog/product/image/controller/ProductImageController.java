package com.ashenox.starter.catalog.product.image.controller;

import com.ashenox.starter.catalog.product.image.dto.ProductImageResponse;
import com.ashenox.starter.catalog.product.image.service.ProductImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/catalog/products/{productId}/images")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','USER')")
@RequiredArgsConstructor
public class ProductImageController {
    private final ProductImageService service;

    @GetMapping
    public List<ProductImageResponse> list(@PathVariable Long productId) {
        return service.list(productId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductImageResponse> upload(@PathVariable Long productId,
                                                        @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(productId, file));
    }
}
