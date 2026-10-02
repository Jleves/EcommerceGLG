package com.ashenox.starter.catalog.product;

import com.ashenox.starter.catalog.product.dto.UpdateProductRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateProductRequestValidationTest {
    @Test
    void appliesTheSameFieldLimitsAsProductCreation() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(request("Arena", 7L, null, "1234567890123.45", true)))
                    .isEmpty();

            for (var invalid : List.of(
                    new Invalid(request(null, 7L, null, "12.50", true), "nombre"),
                    new Invalid(request("   ", 7L, null, "12.50", true), "nombre"),
                    new Invalid(request("x".repeat(161), 7L, null, "12.50", true), "nombre"),
                    new Invalid(request("Arena", null, null, "12.50", true), "categoriaId"),
                    new Invalid(request("Arena", 0L, null, "12.50", true), "categoriaId"),
                    new Invalid(request("Arena", 7L, "x".repeat(1001), "12.50", true), "descripcion"),
                    new Invalid(request("Arena", 7L, null, null, true), "precioReferencia"),
                    new Invalid(request("Arena", 7L, null, "0", true), "precioReferencia"),
                    new Invalid(request("Arena", 7L, null, "-1", true), "precioReferencia"),
                    new Invalid(request("Arena", 7L, null, "12.501", true), "precioReferencia"),
                    new Invalid(request("Arena", 7L, null, "12345678901234.00", true), "precioReferencia"),
                    new Invalid(request("Arena", 7L, null, "12.50", null), "disponible"))) {
                assertThat(validator.validate(invalid.request()).stream()
                        .map(violation -> violation.getPropertyPath().toString()))
                        .as("Campo inválido: %s", invalid.field())
                        .contains(invalid.field());
            }
        }
    }

    private UpdateProductRequest request(String name, Long categoryId, String description,
                                         String price, Boolean available) {
        return new UpdateProductRequest(name, categoryId, description,
                price == null ? null : new BigDecimal(price), available);
    }

    private record Invalid(UpdateProductRequest request, String field) {
    }
}
