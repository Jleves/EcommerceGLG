package com.ashenox.starter.catalog.characteristic.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class ProductCharacteristicId implements Serializable {
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "characteristic_id")
    private Long characteristicId;
}
