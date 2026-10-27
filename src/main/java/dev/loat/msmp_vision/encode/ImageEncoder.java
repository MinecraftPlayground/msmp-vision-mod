package dev.loat.msmp_vision.encode;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Encodes rendered images for transport.
 *
 * <p>Uses a minimal PNG writer (8-bit RGB, no row filtering) with a configurable deflate level instead of
 * {@code ImageIO}, which is roughly twice as slow at full HD for a file that is only slightly smaller.</p>
 */
public final class ImageEncoder {

    private static final byte[] SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    private ImageEncoder() {}

    /**
     * Encodes an image as Base64 PNG with the fastest compression.
     *
     * @param image The image.
     * @return Base64 string without prefix.
     */
    public static String toBase64Png(BufferedImage image) {
        return toBase64Png(image, Deflater.BEST_SPEED);
    }

    /**
     * Encodes an image as Base64 PNG.
     *
     * @param image The image.
     * @param compressionLevel Deflate level, 0 (store) to 9 (smallest, slowest). Values outside are clamped.
     * @return Base64 string without prefix.
     */
    public static String toBase64Png(BufferedImage image, int compressionLevel) {
        try {
            return Base64.getEncoder().encodeToString(toPng(image, Math.max(0, Math.min(9, compressionLevel))));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Encodes an image as PNG bytes.
     *
     * @param image The image.
     * @param level Deflate level, 0 to 9.
     * @return The PNG file.
     * @throws IOException never in practice, all streams are in memory.
     */
    static byte[] toPng(BufferedImage image, int level) throws IOException {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getType() == BufferedImage.TYPE_INT_RGB || image.getType() == BufferedImage.TYPE_INT_ARGB
            ? ((DataBufferInt) image.getRaster().getDataBuffer()).getData()
            : image.getRGB(0, 0, width, height, null, 0, width);

        byte[] raw = new byte[height * (1 + width * 3)];
        int o = 0;
        for (int y = 0; y < height; y++) {
            raw[o++] = 0; // filter type "none"
            int row = y * width;
            for (int x = 0; x < width; x++) {
                int pixel = pixels[row + x];
                raw[o++] = (byte) (pixel >> 16);
                raw[o++] = (byte) (pixel >> 8);
                raw[o++] = (byte) pixel;
            }
        }

        Deflater deflater = new Deflater(level);
        ByteArrayOutputStream compressed = new ByteArrayOutputStream(raw.length / 3);
        try {
            deflater.setInput(raw);
            deflater.finish();
            byte[] buffer = new byte[1 << 16];
            while (!deflater.finished()) {
                int n = deflater.deflate(buffer);
                compressed.write(buffer, 0, n);
            }
        } finally {
            deflater.end();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(compressed.size() + 64);
        out.write(SIGNATURE);
        ByteArrayOutputStream header = new ByteArrayOutputStream(13);
        DataOutputStream headerData = new DataOutputStream(header);
        headerData.writeInt(width);
        headerData.writeInt(height);
        headerData.writeByte(8); // bit depth
        headerData.writeByte(2); // colour type: RGB
        headerData.writeByte(0); // compression
        headerData.writeByte(0); // filter
        headerData.writeByte(0); // interlace
        writeChunk(out, "IHDR", header.toByteArray());
        writeChunk(out, "IDAT", compressed.toByteArray());
        writeChunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        DataOutputStream stream = new DataOutputStream(out);
        byte[] name = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        stream.writeInt(data.length);
        stream.write(name);
        stream.write(data);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        stream.writeInt((int) crc.getValue());
    }
}
