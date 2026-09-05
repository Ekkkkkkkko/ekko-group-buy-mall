package cn.ekko.groupchat.document.service.image;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class RedPlaceholderImageDetectorTest {

    @Test
    void identifiesTheSymmetricMineruRedBrokenImageGlyph() throws Exception {
        assertThat(RedPlaceholderImageDetector.isPlaceholder(asPng(redBrokenImageGlyph()))).isTrue();
    }

    @Test
    void doesNotTreatOrdinaryRedContentAsAPlaceholder() throws Exception {
        BufferedImage redButton = canvas();
        Graphics2D graphics = redButton.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRoundRect(35, 115, 230, 55, 10, 10);
        graphics.dispose();

        assertThat(RedPlaceholderImageDetector.isPlaceholder(asPng(redButton))).isFalse();
    }

    private static BufferedImage redBrokenImageGlyph() {
        BufferedImage image = canvas();
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        int left = 80;
        int top = 90;
        graphics.fillRect(left, top, 42, 25);
        graphics.fillRect(left + 98, top, 42, 25);
        graphics.fillRect(left + 28, top + 20, 84, 80);
        graphics.fillRect(left, top + 95, 42, 25);
        graphics.fillRect(left + 98, top + 95, 42, 25);
        graphics.dispose();
        return image;
    }

    private static BufferedImage canvas() {
        BufferedImage image = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        return image;
    }

    private static byte[] asPng(BufferedImage image) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, "png", output)).isTrue();
        return output.toByteArray();
    }
}
