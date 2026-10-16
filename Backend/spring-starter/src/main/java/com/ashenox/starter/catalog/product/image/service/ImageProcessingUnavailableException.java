package com.ashenox.starter.catalog.product.image.service;

public class ImageProcessingUnavailableException extends RuntimeException {
    public ImageProcessingUnavailableException(Throwable cause) {
        super("No se pudieron generar las variantes WebP.", cause);
    }
}
