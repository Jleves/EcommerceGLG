package com.ashenox.starter.catalog.product.model;

import com.ashenox.starter.catalog.category.model.Category;
import com.ashenox.starter.shared.persistence.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Product extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 160)
    private String nombre;

    @Column(length = 1000)
    private String descripcion;

    @Column(name = "precio_referencia", nullable = false, precision = 15, scale = 2)
    private BigDecimal precioReferencia;

    @Column(nullable = false)
    private boolean disponible;

    @Column(nullable = false)
    @Builder.Default
    private boolean activo = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean destacado = false;
}
