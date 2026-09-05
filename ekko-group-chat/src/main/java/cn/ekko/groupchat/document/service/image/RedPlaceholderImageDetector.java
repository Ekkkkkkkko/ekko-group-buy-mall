package cn.ekko.groupchat.document.service.image;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Detects the high-saturation red broken-image glyph emitted by a subset of
 * MinerU source documents. It is deliberately conservative: ordinary red
 * annotations, buttons, and product photos do not have this symmetric,
 * five-band glyph structure.
 */
public final class RedPlaceholderImageDetector {

    private static final int GRID_SIZE = 7;

    private RedPlaceholderImageDetector() {
    }

    public static boolean isPlaceholder(byte[] content) {
        if (content == null || content.length == 0) {
            return false;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            return image != null && hasRedBrokenImageGlyph(image);
        } catch (IOException | RuntimeException ignored) {
            // The archive parser owns image-format validation. An undecodable image is not a placeholder.
            return false;
        }
    }

    private static boolean hasRedBrokenImageGlyph(BufferedImage image) {
        int minX = image.getWidth(), minY = image.getHeight(), maxX = -1, maxY = -1;
        int redPixels = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (!isGlyphRed(image.getRGB(x, y))) {
                    continue;
                }
                redPixels++;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        if (redPixels < 40) {
            return false;
        }

        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        if (!hasExpectedBounds(image, minX, minY, width, height)) {
            return false;
        }
        return hasExpectedGrid(image, minX, minY, width, height);
    }

    private static boolean hasExpectedBounds(BufferedImage image, int x, int y, int width, int height) {
        double aspect = (double) width / height;
        double relativeWidth = (double) width / image.getWidth();
        double relativeHeight = (double) height / image.getHeight();
        double centerX = (x + width / 2D) / image.getWidth();
        double centerY = (y + height / 2D) / image.getHeight();
        return aspect >= 0.9D && aspect <= 1.4D
                && relativeWidth >= 0.15D && relativeWidth <= 0.75D
                && relativeHeight >= 0.12D && relativeHeight <= 0.70D
                && centerX >= 0.30D && centerX <= 0.70D
                && centerY >= 0.30D && centerY <= 0.70D;
    }

    private static boolean hasExpectedGrid(BufferedImage image, int x, int y, int width, int height) {
        double[][] density = new double[GRID_SIZE][GRID_SIZE];
        for (int gridY = 0; gridY < GRID_SIZE; gridY++) {
            for (int gridX = 0; gridX < GRID_SIZE; gridX++) {
                int fromX = x + gridX * width / GRID_SIZE;
                int toX = x + (gridX + 1) * width / GRID_SIZE;
                int fromY = y + gridY * height / GRID_SIZE;
                int toY = y + (gridY + 1) * height / GRID_SIZE;
                int total = 0;
                int red = 0;
                for (int pixelY = fromY; pixelY < toY; pixelY++) {
                    for (int pixelX = fromX; pixelX < toX; pixelX++) {
                        total++;
                        if (isGlyphRed(image.getRGB(pixelX, pixelY))) {
                            red++;
                        }
                    }
                }
                density[gridY][gridX] = total == 0 ? 0D : (double) red / total;
            }
        }

        // The glyph has four red outer arms and a connected, red centre; the top/bottom centre stays empty.
        return cornerPair(density, 0) && cornerPair(density, GRID_SIZE - 1)
                && density[GRID_SIZE / 2][GRID_SIZE / 2] >= 0.70D
                && density[0][GRID_SIZE / 2] <= 0.15D
                && density[GRID_SIZE - 1][GRID_SIZE / 2] <= 0.15D
                && isSymmetric(density);
    }

    private static boolean cornerPair(double[][] density, int row) {
        return density[row][0] >= 0.50D && density[row][GRID_SIZE - 1] >= 0.50D;
    }

    private static boolean isSymmetric(double[][] density) {
        double difference = 0D;
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                difference += Math.abs(density[y][x] - density[GRID_SIZE - 1 - y][GRID_SIZE - 1 - x]);
            }
        }
        return difference / (GRID_SIZE * GRID_SIZE) <= 0.12D;
    }

    private static boolean isGlyphRed(int rgb) {
        int red = (rgb >>> 16) & 0xff;
        int green = (rgb >>> 8) & 0xff;
        int blue = rgb & 0xff;
        return red >= 220 && green <= 55 && blue <= 55 && red >= green * 3 && red >= blue * 3;
    }
}
