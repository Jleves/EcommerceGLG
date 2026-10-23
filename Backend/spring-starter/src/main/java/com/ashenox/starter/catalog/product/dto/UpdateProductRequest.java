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
    @DecimalMin(value = "0", inclusive = false) @Digits(integer = 13, fraction = 2)
    private BigDecimal precioReferencia;
    private Boolean disponible;
    private boolean precioReferenciaPresente;
    private boolean disponiblePresente;
    private List<@NotNull @Valid ProductCharacteristicRequest> caracteristicas;
    private boolean caracteristicasPresentes;
    @Positive
    private Long subcategoriaId;
    private boolean subcategoriaIdPresente;

    public UpdateProductRequest() {
    }

    public UpdateProductRequest(String nombre, Long categoriaId, String descripcion,
                                BigDecimal precioReferencia, Boolean disponible) {
        this.nombre = nombre;
        this.categoriaId = categoriaId;
        this.descripcion = descripcion;
        this.precioReferencia = precioReferencia;
        this.disponible = disponible;
        this.precioReferenciaPresente = true;
        this.disponiblePresente = true;
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
    public Long subcategoriaId() { return subcategoriaId; }

    @JsonIgnore
    public boolean subcategoriaIdPresente() { return subcategoriaIdPresente; }

    @JsonIgnore
    public boolean caracteristicasPresentes() { return caracteristicasPresentes; }

    @JsonIgnore
    public boolean precioReferenciaPresente() { return precioReferenciaPresente; }

    @JsonIgnore
    public boolean disponiblePresente() { return disponiblePresente; }

    public void setNombre(String nombre) { this.nombre = nombre; }
    public void setCategoriaId(Long categoriaId) { this.categoriaId = categoriaId; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    @JsonSetter("precioReferencia")
    public void setPrecioReferencia(BigDecimal precioReferencia) {
        this.precioReferencia = precioReferencia;
        this.precioReferenciaPresente = true;
    }
    @JsonSetter("disponible")
    public void setDisponible(Boolean disponible) {
        this.disponible = disponible;
        this.disponiblePresente = true;
    }

    @JsonSetter("caracteristicas")
    public void setCaracteristicas(List<ProductCharacteristicRequest> caracteristicas) {
        this.caracteristicas = caracteristicas;
        this.caracteristicasPresentes = true;
    }

    @JsonSetter("subcategoriaId")
    public void setSubcategoriaId(Long subcategoriaId) {
        this.subcategoriaId = subcategoriaId;
        this.subcategoriaIdPresente = true;
    }
}
