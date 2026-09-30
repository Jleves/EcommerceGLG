package com.ashenox.starter.catalog.category.repository;

import com.ashenox.starter.catalog.category.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByNombreNormalizado(String nombreNormalizado);

    boolean existsByNombreNormalizadoAndIdNot(String nombreNormalizado, Long id);

    List<Category> findAllByOrderByNombreAscIdAsc();
}
