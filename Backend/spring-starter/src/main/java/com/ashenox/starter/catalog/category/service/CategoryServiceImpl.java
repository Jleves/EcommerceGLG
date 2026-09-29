package com.ashenox.starter.catalog.category.service;

import com.ashenox.starter.catalog.category.dto.CategoryResponse;
import com.ashenox.starter.catalog.category.dto.CreateCategoryRequest;
import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        String nombre = request.nombre().strip();
        String nombreNormalizado = nombre.toLowerCase(Locale.ROOT);
        if (categoryRepository.existsByNombreNormalizado(nombreNormalizado)) {
            throw new CategoryConflictException();
        }

        Category category = Category.builder()
                .nombre(nombre)
                .nombreNormalizado(nombreNormalizado)
                .descripcion(request.descripcion())
                .icono(request.icono())
                .activo(true)
                .build();
        return CategoryResponse.from(categoryRepository.saveAndFlush(category));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categoryRepository.findAllByOrderByNombreAscIdAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }
}
