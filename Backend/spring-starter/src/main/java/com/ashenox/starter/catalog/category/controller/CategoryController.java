package com.ashenox.starter.catalog.category.controller;

import com.ashenox.starter.catalog.category.dto.CategoryResponse;
import com.ashenox.starter.catalog.category.dto.CreateCategoryRequest;
import com.ashenox.starter.catalog.category.dto.UpdateCategoryRequest;
import com.ashenox.starter.catalog.category.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/catalog/categories")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','USER')")
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(request));
    }

    @GetMapping
    public List<CategoryResponse> list() {
        return categoryService.list();
    }

    @PutMapping("/{id}")
    public CategoryResponse update(@PathVariable Long id, @Valid @RequestBody UpdateCategoryRequest request) {
        return categoryService.update(id, request);
    }

    @PostMapping("/{id}/deactivate")
    public CategoryResponse deactivate(@PathVariable Long id) {
        return categoryService.deactivate(id);
    }

    @PostMapping("/{id}/reactivate")
    public CategoryResponse reactivate(@PathVariable Long id) {
        return categoryService.reactivate(id);
    }
}
