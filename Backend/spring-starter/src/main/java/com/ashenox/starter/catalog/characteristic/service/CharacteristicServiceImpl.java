package com.ashenox.starter.catalog.characteristic.service;

import com.ashenox.starter.catalog.characteristic.dto.CharacteristicResponse;
import com.ashenox.starter.catalog.characteristic.dto.CreateCharacteristicRequest;
import com.ashenox.starter.catalog.characteristic.model.Characteristic;
import com.ashenox.starter.catalog.characteristic.repository.CharacteristicRepository;
import com.ashenox.starter.shared.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CharacteristicServiceImpl implements CharacteristicService {
    private final CharacteristicRepository repository;

    @Override
    @Transactional
    public CharacteristicResponse create(CreateCharacteristicRequest request) {
        String nombre = request.nombre().strip();
        String normalized = nombre.toLowerCase(Locale.ROOT);
        if (repository.existsByNombreNormalizado(normalized)) {
            throw new CharacteristicConflictException();
        }
        return CharacteristicResponse.from(repository.saveAndFlush(Characteristic.builder()
                .nombre(nombre).nombreNormalizado(normalized).build()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CharacteristicResponse> list() {
        return repository.findAllByOrderByNombreAscIdAsc().stream().map(CharacteristicResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CharacteristicResponse getById(Long id) {
        return repository.findById(id).map(CharacteristicResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("La característica solicitada no existe."));
    }
}
