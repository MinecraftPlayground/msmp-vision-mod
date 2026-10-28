package dev.loat.msmp_vision.raster;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * Colour and depth storage. Depth stores {@code 1/w}, so larger means closer and {@code 0} means empty.
 */
public final class Framebuffer {

    private final int width;
    private final int height;
    private final int[] color;
    private final float[] depth;

    /**
     * Creates a cleared framebuffer.
     *
     * @param width Width in pixels.
     * @param height Height in pixels.
     */
    public Framebuffer(int width, int height) {
        this.width = width;
        this.height = height;
        this.color = new int[width * height];
        this.depth = new float[width * height];
    }

    /** @return Width. */
    public int width() { return this.width; }

    /** @return Height. */
    public int height() { return this.height; }

    /** @return Colour buffer ({@code 0xFFRRGGBB}). */
    public int[] color() { return this.color; }

    /** @return Depth buffer ({@code 1/w}). */
    public float[] depth() { return this.depth; }

    /**
     * Downsamples with a box filter.
     *
     * @param factor Supersampling factor.
     * @return The final image.
     */
    public BufferedImage toImage(int factor) {
        int outWidth = this.width / factor;
        int outHeight = this.height / factor;
        BufferedImage image = new BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_RGB);
        int[] target = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        if (factor == 1) {
            System.arraycopy(this.color, 0, target, 0, target.length);
            return image;
        }
        int samples = factor * factor;
        for (int y = 0; y < outHeight; y++) {
            for (int x = 0; x < outWidth; x++) {
                int r = 0;
                int g = 0;
                int b = 0;
                for (int sy = 0; sy < factor; sy++) {
                    int row = (y * factor + sy) * this.width + x * factor;
                    for (int sx = 0; sx < factor; sx++) {
                        int pixel = this.color[row + sx];
                        r += (pixel >> 16) & 0xFF;
                        g += (pixel >> 8) & 0xFF;
                        b += pixel & 0xFF;
                    }
                }
                target[y * outWidth + x] = ((r / samples) << 16) | ((g / samples) << 8) | (b / samples);
            }
        }
        return image;
    }
}
