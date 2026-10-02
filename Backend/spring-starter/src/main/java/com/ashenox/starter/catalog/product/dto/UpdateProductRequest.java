package com.ashenox.starter.catalog.product.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public class UpdateProductRequest {
    @NotBlank @Size(max = 160)
    private String nombre;
    @NotNull @Positive
    private Long categoriaId;
    @Size(max = 1000)
    private String descripcion;
    @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 2)
    private BigDecimal precioReferencia;
    @NotNull
    private Boolean disponible;
    private List<@NotNull @Valid ProductCharacteristicRequest> caracteristicas;
    private boolean caracteristicasPresentes;

    public UpdateProductRequest() {
    }

    public UpdateProductRequest(String nombre, Long categoriaId, String descripcion,
                                BigDecimal precioReferencia, Boolean disponible) {
        this.nombre = nombre;
        this.categoriaId = categoriaId;
        this.descripcion = descripcion;
        this.precioReferencia = precioReferencia;
        this.disponible = disponible;
    }

    public UpdateProductRequest(String nombre, Long categoriaId, String descripcion,
                                BigDecimal precioReferencia, Boolean disponible,
                                List<ProductCharacteristicRequest> caracteristicas) {
        this(nombre, categoriaId, descripcion, precioReferencia, disponible);
        setCaracteristicas(caracteristicas);
    }

    public String nombre() { return nombre; }
    public Long categoriaId() { return categoriaId; }
    public String descripcion() { return descripcion; }
    public BigDecimal precioReferencia() { return precioReferencia; }
    public Boolean disponible() { return disponible; }
    public List<ProductCharacteristicRequest> caracteristicas() { return caracteristicas; }

    @JsonIgnore
    public boolean caracteristicasPresentes() { return caracteristicasPresentes; }

    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setCategoriaId(Long categoriaId) { this.categoriaId = categoriaId; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public void setPrecioReferencia(BigDecimal precioReferencia) { this.precioReferencia = precioReferencia; }
    public void setDisponible(Boolean disponible) { this.disponible = disponible; }

    @JsonSetter("caracteristicas")
    public void setCaracteristicas(List<ProductCharacteristicRequest> caracteristicas) {
        this.caracteristicas = caracteristicas;
        this.caracteristicasPresentes = true;
    }
}
