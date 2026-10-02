package com.ashenox.starter.catalog.characteristic.service;

import com.ashenox.starter.catalog.characteristic.dto.CharacteristicResponse;
import com.ashenox.starter.catalog.characteristic.dto.CreateCharacteristicRequest;
import com.ashenox.starter.catalog.characteristic.model.Characteristic;
import com.ashenox.starter.catalog.characteristic.repository.CharacteristicRepository;
import com.ashenox.starter.catalog.characteristic.repository.ProductCharacteristicRepository;
import com.ashenox.starter.shared.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CharacteristicServiceImpl implements CharacteristicService {
    private final CharacteristicRepository repository;
    private final ProductCharacteristicRepository productCharacteristicRepository;

    @Override
    @Transactional
    public CharacteristicResponse create(CreateCharacteristicRequest request) {
        String nombre = request.nombre().strip();
        String normalized = nombre.toLowerCase(Locale.ROOT);
        if (repository.existsByNombreNormalizado(normalized)) {
            throw new CharacteristicConflictException();
        }
        return CharacteristicResponse.from(repository.saveAndFlush(Characteristic.builder()
                .nombre(nombre).nombreNormalizado(normalized).build()), 0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CharacteristicResponse> list() {
        List<Characteristic> definitions = repository.findAllByOrderByNombreAscIdAsc();
        Map<Long, Long> counts = productCharacteristicRepository.countAllByCharacteristic().stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
        return definitions.stream()
                .map(definition -> CharacteristicResponse.from(definition, counts.getOrDefault(definition.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CharacteristicResponse getById(Long id) {
        Characteristic definition = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("La característica solicitada no existe."));
        return CharacteristicResponse.from(definition, productCharacteristicRepository.countByCharacteristic_Id(id));
    }
}
