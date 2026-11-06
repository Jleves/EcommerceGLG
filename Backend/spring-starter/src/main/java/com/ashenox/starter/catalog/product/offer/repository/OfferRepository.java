package com.ashenox.starter.catalog.product.offer.repository;

import com.ashenox.starter.catalog.product.offer.model.VariantOffer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface OfferRepository extends JpaRepository<VariantOffer, Long> {
    @Query("select offer.variant.product.id from VariantOffer offer where offer.id = :id")
    Optional<Long> findProductIdByOfferId(Long id);

    Optional<VariantOffer> findByVariantId(Long variantId);
    long countByVariantId(Long variantId);

    List<VariantOffer> findByVariantIdIn(Collection<Long> variantIds);

    @Query("select offer from VariantOffer offer join fetch offer.variant variant join fetch variant.product product where product.id in :productIds order by product.id, variant.id")
    List<VariantOffer> findAdministrativeByProductIds(Collection<Long> productIds);

    @Query(value = """
            select p.id from products p
            join product_variants v on v.product_id = p.id
            join variant_offers o on o.variant_id = v.id
            where p.activo = true and v.activo = true
            group by p.id
            order by max((v.precio_referencia - o.precio_oferta) / v.precio_referencia) desc, p.id asc
            """, countQuery = """
            select count(distinct p.id) from products p
            join product_variants v on v.product_id = p.id
            join variant_offers o on o.variant_id = v.id
            where p.activo = true and v.activo = true
            """, nativeQuery = true)
    Page<Long> findPublicProductPage(Pageable pageable);

    @Query(value = "select offer from VariantOffer offer join fetch offer.variant variant join fetch variant.product product order by product.nombre, product.id, variant.id",
            countQuery = "select count(offer) from VariantOffer offer")
    Page<VariantOffer> findAdministrativePage(Pageable pageable);
}
