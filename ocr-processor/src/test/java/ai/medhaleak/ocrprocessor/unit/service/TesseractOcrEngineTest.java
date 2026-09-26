package ai.medhaleak.ocrprocessor.unit.service;

import ai.medhaleak.ocrprocessor.service.TesseractOcrEngine;
import ai.medhaleak.ocrprocessor.testing.Evidence;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class TesseractOcrEngineTest {

    @Test
    void extract_png_readsRenderedText() throws Exception {
        TesseractOcrEngine engine = new TesseractOcrEngine("", "eng");

        BufferedImage image = new BufferedImage(640, 160, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 72));
        g.drawString("HELLO", 40, 110);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);

        byte[] png = out.toByteArray();
        String text = engine.extract(png, "image/png");
        assertThat(text).containsIgnoringCase("HELLO");
        Evidence.record("png " + png.length + " bytes, drawn text HELLO, type=image/png", "Tesseract extract",
                "text=\"" + text.replaceAll("\\s+", " ").trim() + "\"");
    }
}
