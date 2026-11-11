package com.ashenox.starter.catalog.category.service;

import com.ashenox.starter.catalog.category.dto.CategoryResponse;
import com.ashenox.starter.catalog.category.dto.CreateCategoryRequest;
import com.ashenox.starter.catalog.category.dto.UpdateCategoryRequest;
import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.subcategory.repository.SubcategoryRepository;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import com.ashenox.starter.shared.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final SubcategoryRepository subcategories;
    private final ProductRepository products;

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
    @Transactional
    public CategoryResponse update(Long id, UpdateCategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("La categoría solicitada no existe."));
        String nombre = request.nombre().strip();
        String nombreNormalizado = nombre.toLowerCase(Locale.ROOT);
        if (categoryRepository.existsByNombreNormalizadoAndIdNot(nombreNormalizado, id)) {
            throw new CategoryConflictException();
        }

        category.setNombre(nombre);
        category.setNombreNormalizado(nombreNormalizado);
        category.setDescripcion(request.descripcion());
        category.setIcono(request.icono());
        return CategoryResponse.from(categoryRepository.saveAndFlush(category));
    }

    @Override
    @Transactional
    public CategoryResponse deactivate(Long id) {
        return setActive(id, false);
    }

    @Override
    @Transactional
    public CategoryResponse reactivate(Long id) {
        return setActive(id, true);
    }

    private CategoryResponse setActive(Long id, boolean active) {
        Category category = categoryRepository.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("La categoría solicitada no existe."));
        category.setActivo(active);
        return CategoryResponse.from(categoryRepository.saveAndFlush(category));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Category category = categoryRepository.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("La categoría solicitada no existe."));
        long children = subcategories.countByCategoryId(id);
        long linkedProducts = products.countByCategoryId(id);
        if (children > 0 || linkedProducts > 0) {
            throw new CategoryConflictException("No se puede borrar: existen "
                    + (children > 0 ? "subcategorías" : "")
                    + (children > 0 && linkedProducts > 0 ? " y " : "")
                    + (linkedProducts > 0 ? "productos asociados" : "") + ".");
        }
        categoryRepository.delete(category);
        categoryRepository.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> list() {
        return categoryRepository.findAllByOrderByNombreAscIdAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }
}
