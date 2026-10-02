package com.ashenox.starter.catalog.characteristic.controller;

import com.ashenox.starter.catalog.characteristic.dto.CharacteristicResponse;
import com.ashenox.starter.catalog.characteristic.dto.CreateCharacteristicRequest;
import com.ashenox.starter.catalog.characteristic.dto.UpdateCharacteristicRequest;
import com.ashenox.starter.catalog.characteristic.service.CharacteristicService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalog/characteristics")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','USER')")
public class CharacteristicController {
    private final CharacteristicService service;

    @PostMapping
    public ResponseEntity<CharacteristicResponse> create(@Valid @RequestBody CreateCharacteristicRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    public List<CharacteristicResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public CharacteristicResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PutMapping("/{id}")
    public CharacteristicResponse update(@PathVariable Long id, @Valid @RequestBody UpdateCharacteristicRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
