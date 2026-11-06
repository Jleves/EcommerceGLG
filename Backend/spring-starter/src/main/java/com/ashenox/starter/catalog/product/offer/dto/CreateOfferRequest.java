package com.ashenox.starter.catalog.product.offer.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateOfferRequest(@NotNull @Positive Long variantId,
                                 @NotNull @Positive @Digits(integer = 13, fraction = 2) BigDecimal precioOferta) {
}
