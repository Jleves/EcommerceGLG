package com.ashenox.starter.catalog.subcategory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateSubcategoryRequest(
        @NotNull @Positive Long categoriaId,
        @NotBlank @Size(max = 160) String nombre,
        @Size(max = 1000) String descripcion) {
}
