package com.ashenox.starter.catalog.subcategory.service;

import com.ashenox.starter.catalog.subcategory.dto.CreateSubcategoryRequest;
import com.ashenox.starter.catalog.subcategory.dto.SubcategoryDetailResponse;
import com.ashenox.starter.catalog.subcategory.dto.SubcategoryResponse;
import com.ashenox.starter.catalog.subcategory.dto.UpdateSubcategoryRequest;

import java.util.List;

public interface SubcategoryService {
    SubcategoryResponse create(CreateSubcategoryRequest request);
    List<SubcategoryResponse> list(Long categoriaId);
    SubcategoryDetailResponse getById(Long id);
    SubcategoryResponse update(Long id, UpdateSubcategoryRequest request);
    void delete(Long id);
}
