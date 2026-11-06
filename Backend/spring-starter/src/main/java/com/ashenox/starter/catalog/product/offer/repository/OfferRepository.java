package com.ashenox.starter.catalog.product.offer.repository;

import com.ashenox.starter.catalog.product.offer.model.VariantOffer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface OfferRepository extends JpaRepository<VariantOffer, Long> {
    @Query("select offer.variant.product.id from VariantOffer offer where offer.id = :id")
    Optional<Long> findProductIdByOfferId(Long id);

    Optional<VariantOffer> findByVariantId(Long variantId);
    long countByVariantId(Long variantId);

    @Query(value = "select offer from VariantOffer offer join fetch offer.variant variant join fetch variant.product product order by product.nombre, product.id, variant.id",
            countQuery = "select count(offer) from VariantOffer offer")
    Page<VariantOffer> findAdministrativePage(Pageable pageable);
}
