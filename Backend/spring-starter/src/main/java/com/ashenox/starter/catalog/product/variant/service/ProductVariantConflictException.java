package com.ashenox.starter.catalog.product.variant.service;

public class ProductVariantConflictException extends RuntimeException {
    public ProductVariantConflictException(String message) { super(message); }
}
