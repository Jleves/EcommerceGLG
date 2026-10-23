package com.ashenox.starter.catalog.product.variant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateVariantRequest(
        @Size(max = 160) String sku,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 2) BigDecimal precioReferencia,
        @NotNull Boolean disponible,
        @Positive Long caracteristicaId,
        @Size(max = 500) String valor,
        @Size(max = 100) String unidad) {
}
