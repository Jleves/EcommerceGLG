package com.ashenox.starter.catalog.category.repository;

import com.ashenox.starter.catalog.category.model.Category;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select category from Category category where category.id = :id")
    Optional<Category> lockById(Long id);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select category from Category category where category.id = (select s.category.id from Subcategory s where s.id = :subcategoryId)")
    Optional<Category> lockBySubcategoryId(Long subcategoryId);

    boolean existsByNombreNormalizado(String nombreNormalizado);

    boolean existsByNombreNormalizadoAndIdNot(String nombreNormalizado, Long id);

    List<Category> findAllByOrderByNombreAscIdAsc();

    List<Category> findByActivoTrueOrderByNombreAscIdAsc();
}
