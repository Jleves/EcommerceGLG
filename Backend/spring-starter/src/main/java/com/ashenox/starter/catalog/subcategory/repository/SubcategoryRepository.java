package com.ashenox.starter.catalog.subcategory.repository;

import com.ashenox.starter.catalog.subcategory.model.Subcategory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SubcategoryRepository extends JpaRepository<Subcategory, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select subcategory from Subcategory subcategory where subcategory.id = :id")
    Optional<Subcategory> lockById(Long id);

    boolean existsByCategoryIdAndNombreNormalizado(Long categoryId, String nombreNormalizado);

    boolean existsByCategoryIdAndNombreNormalizadoAndIdNot(Long categoryId, String nombreNormalizado, Long id);

    List<Subcategory> findByParentSubcategoryIdOrderByNombreAscIdAsc(Long parentId);
    long countByParentSubcategoryId(Long parentId);
    long countByCategoryId(Long categoryId);

    boolean existsByCategoryIdAndParentSubcategoryIdAndNombreNormalizado(
            Long categoryId, Long parentId, String nombreNormalizado);

    boolean existsByCategoryIdAndParentSubcategoryIdAndNombreNormalizadoAndIdNot(
            Long categoryId, Long parentId, String nombreNormalizado, Long id);

    List<Subcategory> findAllByOrderByCategoryIdAscNombreAscIdAsc();

    @Query("select subcategory from Subcategory subcategory join fetch subcategory.category category "
            + "where category.activo = true order by category.nombre asc, category.id asc, subcategory.nombre asc, subcategory.id asc")
    List<Subcategory> findPublicNavigation();

    List<Subcategory> findByCategoryIdOrderByNombreAscIdAsc(Long categoryId);
}
