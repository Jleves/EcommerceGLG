package com.ashenox.starter.catalog.product.offer.model;

import com.ashenox.starter.catalog.product.variant.model.ProductVariant;
import com.ashenox.starter.shared.persistence.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "variant_offers")
@Getter @Setter
public class VariantOffer extends EntidadAuditable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false, unique = true)
    private ProductVariant variant;

    @Column(name = "precio_oferta", nullable = false, precision = 15, scale = 2)
    private BigDecimal precioOferta;
}
