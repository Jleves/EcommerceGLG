package com.ashenox.starter.catalog.product.image.service;

public class ImageStorageUnavailableException extends RuntimeException {
    public ImageStorageUnavailableException(Throwable cause) { super("No se pudo almacenar la imagen. Consultá la galería antes de reintentar.", cause); }
}
