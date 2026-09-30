package com.ashenox.starter.catalog.category.service;

import com.ashenox.starter.catalog.category.dto.CategoryResponse;
import com.ashenox.starter.catalog.category.dto.CreateCategoryRequest;
import com.ashenox.starter.catalog.category.dto.UpdateCategoryRequest;

import java.util.List;

public interface CategoryService {

    CategoryResponse create(CreateCategoryRequest request);

    CategoryResponse update(Long id, UpdateCategoryRequest request);

    List<CategoryResponse> list();
}
