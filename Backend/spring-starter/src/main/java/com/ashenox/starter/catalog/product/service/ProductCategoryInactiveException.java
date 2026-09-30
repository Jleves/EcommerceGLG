package com.ashenox.starter.catalog.product.service;

public class ProductCategoryInactiveException extends RuntimeException {
    public ProductCategoryInactiveException() {
        super("La categoría solicitada está inactiva.");
    }
}
