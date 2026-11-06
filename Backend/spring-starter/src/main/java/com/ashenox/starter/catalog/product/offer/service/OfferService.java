package com.ashenox.starter.catalog.product.offer.service;

import com.ashenox.starter.catalog.product.offer.dto.OfferPageResponse;
import com.ashenox.starter.catalog.product.offer.dto.OfferResponse;
import com.ashenox.starter.catalog.product.offer.model.VariantOffer;
import com.ashenox.starter.catalog.product.offer.repository.OfferRepository;
import com.ashenox.starter.catalog.product.repository.ProductRepository;
import com.ashenox.starter.catalog.product.variant.model.ProductVariant;
import com.ashenox.starter.catalog.product.variant.repository.ProductVariantRepository;
import com.ashenox.starter.shared.error.BusinessException;
import com.ashenox.starter.shared.error.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class OfferService {
    private final OfferRepository offers;
    private final ProductRepository products;
    private final ProductVariantRepository variants;

    @Transactional(readOnly = true)
    public OfferPageResponse list(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException("La página o el tamaño solicitado no es válido.");
        }
        Page<VariantOffer> result = offers.findAdministrativePage(PageRequest.of(page, size));
        return new OfferPageResponse(result.getContent().stream().map(OfferResponse::from).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public OfferResponse create(Long variantId, BigDecimal price) {
        Long productId = variants.findProductIdByVariantId(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("La variante solicitada no existe."));
        products.lockById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("El producto solicitado no existe."));
        ProductVariant variant = variants.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("La variante solicitada no existe."));
        if (!variant.getProduct().isActivo() || !variant.isActivo()) {
            throw new OfferConflictException("El producto y la variante deben estar activos para crear la oferta.");
        }
        validatePrice(price, variant.getPrecioReferencia());
        if (offers.findByVariantId(variantId).isPresent()) {
            throw new OfferConflictException("La variante ya tiene una oferta.");
        }
        VariantOffer offer = new VariantOffer();
        offer.setVariant(variant);
        offer.setPrecioOferta(price);
        return OfferResponse.from(offers.saveAndFlush(offer));
    }

    @Transactional
    public OfferResponse update(Long id, BigDecimal price) {
        Long productId = offers.findProductIdByOfferId(id)
                .orElseThrow(() -> new ResourceNotFoundException("La oferta solicitada no existe."));
        products.lockById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("El producto solicitado no existe."));
        VariantOffer offer = offers.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("La oferta solicitada no existe."));
        validatePrice(price, offer.getVariant().getPrecioReferencia());
        offer.setPrecioOferta(price);
        return OfferResponse.from(offers.saveAndFlush(offer));
    }

    @Transactional
    public void delete(Long id) {
        Long productId = offers.findProductIdByOfferId(id)
                .orElseThrow(() -> new ResourceNotFoundException("La oferta solicitada no existe."));
        products.lockById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("El producto solicitado no existe."));
        VariantOffer offer = offers.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("La oferta solicitada no existe."));
        offers.delete(offer);
        offers.flush();
    }

    public void validateBasePrice(Long variantId, BigDecimal basePrice) {
        offers.findByVariantId(variantId).ifPresent(offer -> {
            if (basePrice.compareTo(offer.getPrecioOferta()) <= 0) {
                throw new OfferConflictException("El precio de referencia debe superar el precio de oferta vigente.");
            }
        });
    }

    private void validatePrice(BigDecimal price, BigDecimal basePrice) {
        if (price.compareTo(basePrice) >= 0) {
            throw new OfferConflictException("El precio de oferta debe ser menor que el precio de referencia.");
        }
    }
}
