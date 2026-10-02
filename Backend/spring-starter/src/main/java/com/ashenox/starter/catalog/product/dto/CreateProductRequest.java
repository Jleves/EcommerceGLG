package com.ashenox.starter.catalog.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.util.List;

public record CreateProductRequest(
        @NotBlank @Size(max = 160) String nombre,
        @NotNull @Positive Long categoriaId,
        @Size(max = 1000) String descripcion,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 2) BigDecimal precioReferencia,
        @NotNull Boolean disponible,
        List<@NotNull @Valid ProductCharacteristicRequest> caracteristicas) {
    public CreateProductRequest(String nombre, Long categoriaId, String descripcion,
                                BigDecimal precioReferencia, Boolean disponible) {
        this(nombre, categoriaId, descripcion, precioReferencia, disponible, null);
    }
}
