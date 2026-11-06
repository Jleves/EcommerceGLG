package com.ashenox.starter.catalog.product.offer.controller;

import com.ashenox.starter.catalog.product.offer.dto.CreateOfferRequest;
import com.ashenox.starter.catalog.product.offer.dto.OfferPageResponse;
import com.ashenox.starter.catalog.product.offer.dto.OfferResponse;
import com.ashenox.starter.catalog.product.offer.dto.UpdateOfferRequest;
import com.ashenox.starter.catalog.product.offer.service.OfferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/offers")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','USER')")
public class OfferController {
    private final OfferService service;

    @GetMapping
    public OfferPageResponse list(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size);
    }

    @PostMapping
    public ResponseEntity<OfferResponse> create(@Valid @RequestBody CreateOfferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request.variantId(), request.precioOferta()));
    }

    @PutMapping("/{id}")
    public OfferResponse update(@PathVariable Long id, @Valid @RequestBody UpdateOfferRequest request) {
        return service.update(id, request.precioOferta());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
