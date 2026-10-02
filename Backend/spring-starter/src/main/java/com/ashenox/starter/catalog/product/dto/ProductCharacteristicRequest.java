package com.ashenox.starter.catalog.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ProductCharacteristicRequest(
        @NotNull @Positive Long caracteristicaId,
        @NotBlank @Size(max = 500) String valor,
        @Size(max = 100) String unidad) {
}
