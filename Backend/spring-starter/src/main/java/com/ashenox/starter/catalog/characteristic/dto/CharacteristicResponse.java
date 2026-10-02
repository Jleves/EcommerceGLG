package com.ashenox.starter.catalog.characteristic.dto;

import com.ashenox.starter.catalog.characteristic.model.Characteristic;

public record CharacteristicResponse(Long id, String nombre) {
    public static CharacteristicResponse from(Characteristic characteristic) {
        return new CharacteristicResponse(characteristic.getId(), characteristic.getNombre());
    }
}
