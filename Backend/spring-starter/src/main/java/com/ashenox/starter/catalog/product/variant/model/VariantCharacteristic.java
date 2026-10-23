package com.ashenox.starter.catalog.product.variant.model;

import com.ashenox.starter.catalog.characteristic.model.Characteristic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "variant_characteristics")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VariantCharacteristic {
    @Id
    @Column(name = "variant_id")
    private Long variantId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "characteristic_id", nullable = false)
    private Characteristic characteristic;

    @Column(nullable = false, length = 500)
    private String valor;

    @Column(name = "valor_normalizado", nullable = false, length = 500)
    private String valorNormalizado;

    @Column(length = 100)
    private String unidad;

    @Column(name = "unidad_normalizada", length = 100)
    private String unidadNormalizada;
}
