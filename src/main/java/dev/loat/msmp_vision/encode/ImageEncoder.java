package dev.loat.msmp_vision.encode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;

/**
 * Encodes rendered images for transport.
 */
public final class ImageEncoder {

    private ImageEncoder() {}

    /**
     * Encodes an image as Base64 PNG.
     *
     * @param image The image.
     * @return Base64 string without prefix.
     */
    public static String toBase64Png(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(image.getWidth() * image.getHeight());
            if (!ImageIO.write(image, "png", out)) {
                throw new IOException("No PNG writer available");
            }
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
