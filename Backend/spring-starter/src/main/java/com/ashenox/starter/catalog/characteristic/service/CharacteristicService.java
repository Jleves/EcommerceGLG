package com.ashenox.starter.catalog.characteristic.service;

import com.ashenox.starter.catalog.characteristic.dto.CharacteristicResponse;
import com.ashenox.starter.catalog.characteristic.dto.CreateCharacteristicRequest;

import java.util.List;

public interface CharacteristicService {
    CharacteristicResponse create(CreateCharacteristicRequest request);
    List<CharacteristicResponse> list();
    CharacteristicResponse getById(Long id);
}
