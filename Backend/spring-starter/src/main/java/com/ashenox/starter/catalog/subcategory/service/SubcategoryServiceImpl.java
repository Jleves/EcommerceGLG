package com.ashenox.starter.catalog.subcategory.service;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.catalog.category.repository.CategoryRepository;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import com.ashenox.starter.catalog.subcategory.dto.CreateSubcategoryRequest;
import com.ashenox.starter.catalog.subcategory.dto.SubcategoryDetailResponse;
import com.ashenox.starter.catalog.subcategory.dto.SubcategoryResponse;
import com.ashenox.starter.catalog.subcategory.dto.UpdateSubcategoryRequest;
import com.ashenox.starter.catalog.subcategory.model.Subcategory;
import com.ashenox.starter.catalog.subcategory.repository.SubcategoryRepository;
import com.ashenox.starter.shared.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SubcategoryServiceImpl implements SubcategoryService {
    private final SubcategoryRepository subcategories;
    private final CategoryRepository categories;
    private final ProductRepository products;

    @Override
    @Transactional
    public SubcategoryResponse create(CreateSubcategoryRequest request) {
        Category parent = categories.findById(request.categoriaId())
                .orElseThrow(() -> new ResourceNotFoundException("La categoría solicitada no existe."));
        if (!parent.isActivo()) {
            throw new SubcategoryConflictException("La categoría solicitada está inactiva.");
        }
        String nombre = request.nombre().strip();
        String normalized = nombre.toLowerCase(Locale.ROOT);
        if (subcategories.existsByCategoryIdAndNombreNormalizado(parent.getId(), normalized)) {
            throw new SubcategoryConflictException("Ya existe una subcategoría con ese nombre en la categoría.");
        }
        Subcategory subcategory = Subcategory.builder().category(parent).nombre(nombre)
                .nombreNormalizado(normalized).descripcion(request.descripcion()).build();
        return SubcategoryResponse.from(subcategories.saveAndFlush(subcategory));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SubcategoryResponse> list(Long categoriaId) {
        List<Subcategory> found = categoriaId == null
                ? subcategories.findAllByOrderByCategoryIdAscNombreAscIdAsc()
                : subcategories.findByCategoryIdOrderByNombreAscIdAsc(categoriaId);
        return found.stream().map(SubcategoryResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SubcategoryDetailResponse getById(Long id) {
        Subcategory subcategory = subcategories.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("La subcategoría solicitada no existe."));
        return SubcategoryDetailResponse.from(subcategory, products.countBySubcategoryId(id));
    }

    @Override
    @Transactional
    public SubcategoryResponse update(Long id, UpdateSubcategoryRequest request) {
        Subcategory subcategory = subcategories.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("La subcategoría solicitada no existe."));
        String nombre = request.nombre().strip();
        String normalized = nombre.toLowerCase(Locale.ROOT);
        if (subcategories.existsByCategoryIdAndNombreNormalizadoAndIdNot(
                subcategory.getCategory().getId(), normalized, id)) {
            throw new SubcategoryConflictException("Ya existe una subcategoría con ese nombre en la categoría.");
        }
        subcategory.setNombre(nombre);
        subcategory.setNombreNormalizado(normalized);
        subcategory.setDescripcion(request.descripcion());
        return SubcategoryResponse.from(subcategories.saveAndFlush(subcategory));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Subcategory subcategory = subcategories.lockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("La subcategoría solicitada no existe."));
        products.clearSubcategoryAndTouchUpdatedAt(id);
        subcategories.delete(subcategory);
        subcategories.flush();
    }
}
