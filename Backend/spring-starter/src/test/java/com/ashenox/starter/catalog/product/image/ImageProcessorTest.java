package com.ashenox.starter.catalog.product.image;

import com.ashenox.starter.catalog.product.image.service.ImageProcessor;
import com.ashenox.starter.catalog.product.image.service.InvalidProductImageException;
import com.ashenox.starter.shared.config.AppProperties;
import com.sksamuel.scrimage.ImmutableImage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageProcessorTest {
    private final AppProperties properties = new AppProperties();
    private final ImageProcessor processor = new ImageProcessor(properties);

    @Test
    void jpegPngAndWebpProduceTwoBoundedWebpVariants() throws Exception {
        BufferedImage source = new BufferedImage(1600, 800, BufferedImage.TYPE_INT_RGB);
        source.setRGB(0, 0, Color.RED.getRGB());
        byte[] jpeg = encode(source, "jpeg");
        byte[] png = encode(source, "png");
        byte[] webp = processor.validateAndGenerate(file(jpeg, "image/jpeg")).detail();
        for (byte[] input : new byte[][]{jpeg, png, webp}) {
            var result = processor.validateAndGenerate(file(input, "application/octet-stream"));
            assertThat(result.thumbnail()).startsWith("RIFF".getBytes());
            assertThat(result.detail()).startsWith("RIFF".getBytes());
            assertThat(ImmutableImage.loader().fromBytes(result.thumbnail()).width).isEqualTo(400);
            assertThat(ImmutableImage.loader().fromBytes(result.detail()).width).isEqualTo(1200);
        }
    }

    @Test
    void appliesExifOrientationAndRemovesSourceExif() throws Exception {
        BufferedImage source = new BufferedImage(30, 60, BufferedImage.TYPE_INT_RGB);
        byte[] jpeg = encode(source, "jpeg");
        byte[] exif = new byte[]{'E','x','i','f',0,0,'I','I',42,0,8,0,0,0,1,0,
                0x12,1,3,0,1,0,0,0,6,0,0,0,0,0,0,0};
        byte[] oriented = new byte[jpeg.length + exif.length + 4];
        System.arraycopy(jpeg, 0, oriented, 0, 2);
        oriented[2] = (byte) 0xff; oriented[3] = (byte) 0xe1;
        int length = exif.length + 2;
        oriented[4] = (byte) (length >>> 8); oriented[5] = (byte) length;
        System.arraycopy(exif, 0, oriented, 6, exif.length);
        System.arraycopy(jpeg, 2, oriented, 6 + exif.length, jpeg.length - 2);
        var result = processor.validateAndGenerate(file(oriented, "image/jpeg"));
        ImmutableImage output = ImmutableImage.loader().fromBytes(result.detail());
        assertThat(output.width).isEqualTo(60);
        assertThat(output.height).isEqualTo(30);
        assertThat(new String(result.detail(), java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
    }

    @Test
    void keepsPngTransparencyInGeneratedWebp() throws Exception {
        BufferedImage source = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, 0x00000000);
        source.setRGB(1, 1, 0xffff0000);
        var result = processor.validateAndGenerate(file(encode(source, "png"), "image/png"));
        BufferedImage decoded = ImmutableImage.loader().fromBytes(result.detail()).awt();
        assertThat(decoded.getColorModel().hasAlpha()).isTrue();
        assertThat((decoded.getRGB(0, 0) >>> 24) & 255).isZero();
    }

    @Test
    void rejectsWrongTypeCorruptionAnimationSizeAndPixelLimit() throws Exception {
        byte[] png = encode(new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB), "png");
        assertThatThrownBy(() -> processor.validateAndGenerate(file("fake".getBytes(), "image/png")))
                .isInstanceOf(InvalidProductImageException.class);
        assertThatThrownBy(() -> processor.validateAndGenerate(file(Arrays.copyOf(png, 20), "image/png")))
                .isInstanceOf(InvalidProductImageException.class);
        byte[] oversized = Arrays.copyOf(png, 10 * 1024 * 1024 + 1);
        assertThatThrownBy(() -> processor.validateAndGenerate(file(oversized, "image/png")))
                .isInstanceOf(InvalidProductImageException.class);
        properties.getImages().setMaxPixels(399);
        assertThatThrownBy(() -> processor.validateAndGenerate(file(png, "image/png")))
                .isInstanceOf(InvalidProductImageException.class);
        properties.getImages().setMaxPixels(20_000_000);
        byte[] animated = new byte[30];
        System.arraycopy("RIFF".getBytes(), 0, animated, 0, 4);
        System.arraycopy("WEBPVP8X".getBytes(), 0, animated, 8, 8);
        animated[20] = 2;
        assertThatThrownBy(() -> processor.validateAndGenerate(file(animated, "image/webp")))
                .isInstanceOf(InvalidProductImageException.class);
    }

    private MockMultipartFile file(byte[] bytes, String type) {
        return new MockMultipartFile("file", "photo.bin", type, bytes);
    }

    private byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }
}
