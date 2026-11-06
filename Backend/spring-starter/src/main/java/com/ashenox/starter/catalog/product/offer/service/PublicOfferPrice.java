package com.ashenox.starter.catalog.product.offer.service;

import com.ashenox.starter.catalog.product.offer.model.VariantOffer;
import com.ashenox.starter.catalog.product.variant.model.ProductVariant;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.Map;

public final class PublicOfferPrice {
    private PublicOfferPrice() {}

    public static BigDecimal price(VariantOffer offer) {
        return offer == null ? null : offer.getPrecioOferta();
    }

    public static Integer percent(VariantOffer offer) {
        if (offer == null) return null;
        BigDecimal base = offer.getVariant().getPrecioReferencia();
        return base.subtract(offer.getPrecioOferta()).multiply(BigDecimal.valueOf(100))
                .divide(base, 0, RoundingMode.DOWN).intValueExact();
    }

    public static Comparator<ProductVariant> preferred(Map<Long, VariantOffer> offers) {
        return (left, right) -> {
            VariantOffer leftOffer = offers.get(left.getId());
            VariantOffer rightOffer = offers.get(right.getId());
            if (leftOffer != null && rightOffer == null) return -1;
            if (leftOffer == null && rightOffer != null) return 1;
            if (leftOffer != null) {
                // Compare final/base ratios by cross multiplication, without rounding the discount.
                int discount = leftOffer.getPrecioOferta().multiply(right.getPrecioReferencia())
                        .compareTo(rightOffer.getPrecioOferta().multiply(left.getPrecioReferencia()));
                if (discount != 0) return discount;
                int available = Boolean.compare(!left.isDisponible(), !right.isDisponible());
                return available != 0 ? available : left.getId().compareTo(right.getId());
            }
            return Comparator.comparing((ProductVariant variant) -> !variant.isDisponible())
                    .thenComparing(ProductVariant::getPrecioReferencia)
                    .thenComparing(ProductVariant::getId).compare(left, right);
        };
    }
}
