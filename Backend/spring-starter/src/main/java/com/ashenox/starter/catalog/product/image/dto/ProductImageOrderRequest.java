package com.ashenox.starter.catalog.product.image.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ProductImageOrderRequest(@NotNull List<@NotNull Long> imageIds) {}
