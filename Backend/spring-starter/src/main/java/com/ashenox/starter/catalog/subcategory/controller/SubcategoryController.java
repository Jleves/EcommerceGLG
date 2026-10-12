package com.ashenox.starter.catalog.subcategory.controller;

import com.ashenox.starter.catalog.subcategory.dto.CreateSubcategoryRequest;
import com.ashenox.starter.catalog.subcategory.dto.SubcategoryDetailResponse;
import com.ashenox.starter.catalog.subcategory.dto.SubcategoryResponse;
import com.ashenox.starter.catalog.subcategory.dto.UpdateSubcategoryRequest;
import com.ashenox.starter.catalog.subcategory.service.SubcategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalog/subcategories")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','USER')")
public class SubcategoryController {
    private final SubcategoryService service;

    @PostMapping
    public ResponseEntity<SubcategoryResponse> create(@Valid @RequestBody CreateSubcategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    public List<SubcategoryResponse> list(@RequestParam(required = false) Long categoriaId) {
        return service.list(categoriaId);
    }

    @GetMapping("/{id}")
    public SubcategoryDetailResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PutMapping("/{id}")
    public SubcategoryResponse update(@PathVariable Long id, @Valid @RequestBody UpdateSubcategoryRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
