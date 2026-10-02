package com.ashenox.starter.catalog.characteristic.model;

import com.ashenox.starter.catalog.product.model.Product;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "product_characteristics")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductCharacteristic {
    @EmbeddedId
    private ProductCharacteristicId id;

    @MapsId("productId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @MapsId("characteristicId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "characteristic_id", nullable = false)
    private Characteristic characteristic;

    @Column(nullable = false, length = 500)
    private String valor;

    @Column(length = 100)
    private String unidad;
}
