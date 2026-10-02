package com.ashenox.starter.catalog.characteristic.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCharacteristicRequest(@NotBlank @Size(max = 160) String nombre) {
}
