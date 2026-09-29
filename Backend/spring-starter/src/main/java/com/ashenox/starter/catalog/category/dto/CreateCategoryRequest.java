package com.ashenox.starter.catalog.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 160) String nombre,
        @Size(max = 1000) String descripcion,
        @NotBlank @Size(max = 100) String icono) {
}
