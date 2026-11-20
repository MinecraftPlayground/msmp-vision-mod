package dev.loat.msmp_vision.assets;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;

/**
 * Converts decoded PNGs to ARGB without the colour conversion of {@link BufferedImage#getRGB(int, int, int, int, int[], int, int)}.
 *
 * <p>For greyscale images (ex. {@code grass_block_top.png}) Java treats the values as linear light and converts them
 * to sRGB, which makes them much brighter and flattens their contrast (a texture with the values 112..195 becomes
 * 177..227). Minecraft uses the stored values as they are, so greyscale images are read from the raster directly.</p>
 */
public final class ImageDecoder {

    private ImageDecoder() {}

    /**
     * Returns the pixels of an image as {@code 0xAARRGGBB}, row-major.
     *
     * @param image The decoded image.
     * @return {@code width * height} pixels.
     */
    public static int[] argb(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        ColorModel model = image.getColorModel();
        if (model.getColorSpace().getType() != ColorSpace.TYPE_GRAY) {
            return image.getRGB(0, 0, width, height, null, 0, width);
        }
        Raster raster = image.getRaster();
        boolean alpha = model.hasAlpha() && raster.getNumBands() > 1;
        int maxValue = (1 << model.getComponentSize(0)) - 1;
        int maxAlpha = alpha ? (1 << model.getComponentSize(1)) - 1 : 1;
        int[] pixels = new int[width * height];
        int[] sample = new int[raster.getNumBands()];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                raster.getPixel(x, y, sample);
                int grey = sample[0] * 255 / maxValue;
                int a = alpha ? sample[1] * 255 / maxAlpha : 255;
                pixels[y * width + x] = (a << 24) | (grey << 16) | (grey << 8) | grey;
            }
        }
        return pixels;
    }
}
