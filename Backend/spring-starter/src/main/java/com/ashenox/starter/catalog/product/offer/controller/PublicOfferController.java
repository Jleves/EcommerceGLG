package com.ashenox.starter.catalog.product.offer.controller;

import com.ashenox.starter.catalog.product.dto.PublicProductPageResponse;
import com.ashenox.starter.catalog.product.offer.service.PublicOfferService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/offers")
@RequiredArgsConstructor
public class PublicOfferController {
    private final PublicOfferService service;

    @GetMapping
    public PublicProductPageResponse list(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size);
    }
}
