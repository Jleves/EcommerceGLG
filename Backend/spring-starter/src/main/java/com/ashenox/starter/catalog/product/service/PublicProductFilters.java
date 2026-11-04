package com.ashenox.starter.catalog.product.service;

import com.ashenox.starter.catalog.characteristic.model.ProductCharacteristic;
import com.ashenox.starter.catalog.product.model.Product;
import com.ashenox.starter.catalog.product.variant.model.ProductVariant;
import com.ashenox.starter.catalog.product.variant.model.VariantCharacteristic;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class PublicProductFilters {
    private PublicProductFilters() {}

    record Attribute(Long id, String value) {}

    static Specification<Product> of(String query, Long categoryId, Long subcategoryId, String brand, List<Attribute> attributes) {
        return (root, criteria, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            conditions.add(cb.isTrue(root.get("activo")));
            Subquery<Long> activeVariant = criteria.subquery(Long.class);
            Root<ProductVariant> variant = activeVariant.from(ProductVariant.class);
            activeVariant.select(variant.get("id")).where(cb.equal(variant.get("product"), root), cb.isTrue(variant.get("activo")));
            conditions.add(cb.exists(activeVariant));

            if (categoryId != null) conditions.add(cb.equal(root.get("category").get("id"), categoryId));
            if (subcategoryId != null) conditions.add(cb.equal(root.get("subcategory").get("id"), subcategoryId));
            if (brand != null) conditions.add(characteristicMatches(root, criteria, cb, null, brand, true));
            for (Attribute attribute : attributes) {
                conditions.add(cb.or(characteristicMatches(root, criteria, cb, attribute.id(), attribute.value(), false),
                        variantCharacteristicMatches(root, criteria, cb, attribute.id(), attribute.value())));
            }
            if (query != null) {
                String pattern = "%" + escape(query.toLowerCase(Locale.ROOT)) + "%";
                Predicate name = cb.like(cb.lower(root.get("nombre")), pattern, '!');
                Predicate description = cb.like(cb.lower(root.get("descripcion")), pattern, '!');
                Predicate characteristic = characteristicContains(root, criteria, cb, pattern);
                Predicate variantCharacteristic = variantCharacteristicContains(root, criteria, cb, pattern);
                conditions.add(cb.or(name, description, characteristic, variantCharacteristic));
            }
            return cb.and(conditions.toArray(Predicate[]::new));
        };
    }

    private static Predicate characteristicMatches(Root<Product> root, jakarta.persistence.criteria.CriteriaQuery<?> criteria,
                                                   jakarta.persistence.criteria.CriteriaBuilder cb, Long id, String value,
                                                   boolean brand) {
        Subquery<Long> subquery = criteria.subquery(Long.class);
        Root<ProductCharacteristic> pc = subquery.from(ProductCharacteristic.class);
        List<Predicate> conditions = new ArrayList<>();
        conditions.add(cb.equal(pc.get("product"), root));
        conditions.add(brand ? cb.equal(cb.lower(pc.get("characteristic").get("nombre")), "marca")
                : cb.equal(pc.get("characteristic").get("id"), id));
        conditions.add(cb.equal(cb.lower(pc.get("valor")), value.toLowerCase(Locale.ROOT)));
        subquery.select(pc.get("product").get("id")).where(conditions.toArray(Predicate[]::new));
        return cb.exists(subquery);
    }

    private static Predicate characteristicContains(Root<Product> root, jakarta.persistence.criteria.CriteriaQuery<?> criteria,
                                                    jakarta.persistence.criteria.CriteriaBuilder cb, String pattern) {
        Subquery<Long> subquery = criteria.subquery(Long.class);
        Root<ProductCharacteristic> pc = subquery.from(ProductCharacteristic.class);
        subquery.select(pc.get("product").get("id")).where(cb.equal(pc.get("product"), root),
                cb.like(cb.lower(pc.get("valor")), pattern, '!'));
        return cb.exists(subquery);
    }

    private static Predicate variantCharacteristicMatches(Root<Product> root,
            jakarta.persistence.criteria.CriteriaQuery<?> criteria, jakarta.persistence.criteria.CriteriaBuilder cb,
            Long id, String value) {
        Subquery<Long> subquery = criteria.subquery(Long.class);
        Root<VariantCharacteristic> vc = subquery.from(VariantCharacteristic.class);
        subquery.select(vc.get("variant").get("id")).where(cb.equal(vc.get("variant").get("product"), root),
                cb.isTrue(vc.get("variant").get("activo")), cb.equal(vc.get("characteristic").get("id"), id),
                cb.equal(cb.lower(vc.get("valor")), value.toLowerCase(Locale.ROOT)));
        return cb.exists(subquery);
    }

    private static Predicate variantCharacteristicContains(Root<Product> root,
            jakarta.persistence.criteria.CriteriaQuery<?> criteria, jakarta.persistence.criteria.CriteriaBuilder cb,
            String pattern) {
        Subquery<Long> subquery = criteria.subquery(Long.class);
        Root<VariantCharacteristic> vc = subquery.from(VariantCharacteristic.class);
        subquery.select(vc.get("variant").get("id")).where(cb.equal(vc.get("variant").get("product"), root),
                cb.isTrue(vc.get("variant").get("activo")), cb.like(cb.lower(vc.get("valor")), pattern, '!'));
        return cb.exists(subquery);
    }

    private static String escape(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
