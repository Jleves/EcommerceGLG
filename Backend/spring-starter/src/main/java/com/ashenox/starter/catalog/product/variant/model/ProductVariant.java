package com.ashenox.starter.catalog.product.variant.model;

import com.ashenox.starter.catalog.product.model.Product;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

@Entity
@Table(name = "product_variants")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @SuperBuilder
public class ProductVariant extends EntidadAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(length = 160)
    private String sku;

    @Column(name = "sku_normalizado", length = 160, unique = true)
    private String skuNormalizado;

    @Column(name = "precio_referencia", nullable = false, precision = 15, scale = 2)
    private BigDecimal precioReferencia;

    @Column(nullable = false)
    private boolean disponible;

    @Column(nullable = false)
    private boolean activo;
}
