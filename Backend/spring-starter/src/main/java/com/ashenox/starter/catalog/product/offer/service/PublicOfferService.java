package com.ashenox.starter.catalog.product.offer.service;

import com.ashenox.starter.catalog.product.dto.PublicProductPageResponse;
import com.ashenox.starter.catalog.product.offer.repository.OfferRepository;
import com.ashenox.starter.catalog.product.service.PublicProductService;
import com.ashenox.starter.shared.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PublicOfferService {
    private final OfferRepository offers;
    private final PublicProductService products;

    @Transactional(readOnly = true)
    public PublicProductPageResponse list(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException("La página o el tamaño solicitado no es válido.");
        }
        return products.offersPage(offers.findPublicProductPage(PageRequest.of(page, size)), page, size);
    }
}
