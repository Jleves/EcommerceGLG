package com.ashenox.starter.catalog.product.image.service;

import com.ashenox.starter.shared.config.AppProperties;
import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.webp.WebpWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

@Component
@RequiredArgsConstructor
public class ImageProcessor {
    private final AppProperties properties;

    public record Variants(byte[] thumbnail, byte[] detail) {}

    public Variants validateAndGenerate(MultipartFile file) {
        long maxSize = properties.getImages().getMaxFileSizeBytes();
        if (file == null || file.isEmpty() || file.getSize() > maxSize) {
            throw new InvalidProductImageException("El archivo está vacío o supera 10 MiB.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new InvalidProductImageException("No se pudo leer el archivo recibido.");
        }
        if (bytes.length == 0 || bytes.length > maxSize) {
            throw new InvalidProductImageException("El archivo está vacío o supera 10 MiB.");
        }
        ImmutableImage pixels;
        try {
            int[] dimensions = dimensions(bytes);
            if (dimensions[0] < 1 || dimensions[1] < 1 ||
                    (long) dimensions[0] * dimensions[1] > properties.getImages().getMaxPixels()) {
                throw new InvalidProductImageException("La imagen supera el límite de 20 megapíxeles.");
            }
            // Scrimage applies EXIF orientation while loading. Rewrapping the pixels drops source metadata.
            ImmutableImage oriented = ImmutableImage.loader().fromBytes(bytes);
            pixels = ImmutableImage.fromAwt(oriented.awt());
        } catch (InvalidProductImageException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new InvalidProductImageException("El archivo no es una imagen JPEG, PNG o WebP válida.");
        }
        try {
            WebpWriter writer = new WebpWriter().withQ(80);
            byte[] thumb = resize(pixels, properties.getImages().getThumbnailMaxWidth()).bytes(writer);
            byte[] detail = resize(pixels, properties.getImages().getDetailMaxWidth()).bytes(writer);
            if (!isWebp(thumb) || !isWebp(detail)) {
                throw new IllegalStateException("El codificador devolvió datos que no son WebP.");
            }
            return new Variants(thumb, detail);
        } catch (IOException | RuntimeException exception) {
            throw new ImageProcessingUnavailableException(exception);
        }
    }

    private ImmutableImage resize(ImmutableImage image, int maxWidth) {
        return image.width > maxWidth ? image.scaleToWidth(maxWidth) : image;
    }

    private int[] dimensions(byte[] bytes) throws IOException {
        if (isWebp(bytes)) return webpDimensions(bytes);
        boolean jpeg = bytes.length > 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216;
        boolean png = bytes.length > 8 && Arrays.equals(Arrays.copyOf(bytes, 8),
                new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
        if (!jpeg && !png) throw new InvalidProductImageException("Solo se aceptan JPEG, PNG y WebP.");
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new InvalidProductImageException("Imagen corrupta.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName();
                if (!(jpeg && format.equalsIgnoreCase("JPEG")) && !(png && format.equalsIgnoreCase("PNG"))) {
                    throw new InvalidProductImageException("El contenido no coincide con el formato de imagen.");
                }
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        }
    }

    private boolean isWebp(byte[] bytes) {
        return bytes.length >= 25 && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP");
    }

    private int[] webpDimensions(byte[] b) {
        if (b.length >= 30 && ascii(b, 12, "VP8X")) {
            if ((b[20] & 0x02) != 0) throw new InvalidProductImageException("No se admiten imágenes WebP animadas.");
            return new int[]{1 + u24(b, 24), 1 + u24(b, 27)};
        }
        if (ascii(b, 12, "VP8L") && (b[20] & 255) == 0x2f) {
            int width = 1 + ((b[21] & 255) | ((b[22] & 0x3f) << 8));
            int height = 1 + (((b[22] & 0xc0) >>> 6) | ((b[23] & 255) << 2) | ((b[24] & 0x0f) << 10));
            return new int[]{width, height};
        }
        if (ascii(b, 12, "VP8 ") && b.length >= 30 && (b[23] & 255) == 0x9d &&
                (b[24] & 255) == 0x01 && (b[25] & 255) == 0x2a) {
            return new int[]{((b[26] & 255) | ((b[27] & 0x3f) << 8)),
                    ((b[28] & 255) | ((b[29] & 0x3f) << 8))};
        }
        throw new InvalidProductImageException("Imagen WebP corrupta o no admitida.");
    }

    private int u24(byte[] b, int offset) {
        return (b[offset] & 255) | ((b[offset + 1] & 255) << 8) | ((b[offset + 2] & 255) << 16);
    }

    private boolean ascii(byte[] bytes, int offset, String expected) {
        if (bytes.length < offset + expected.length()) return false;
        for (int i = 0; i < expected.length(); i++) {
            if (bytes[offset + i] != expected.charAt(i)) return false;
        }
        return true;
    }
}
