package com.ashenox.starter.catalog.product.dto;

import com.ashenox.starter.catalog.characteristic.model.ProductCharacteristic;

public record ProductCharacteristicResponse(Long caracteristicaId, String nombre, String valor, String unidad) {
    public static ProductCharacteristicResponse from(ProductCharacteristic item) {
        return new ProductCharacteristicResponse(item.getCharacteristic().getId(),
                item.getCharacteristic().getNombre(), item.getValor(), item.getUnidad());
    }
}
