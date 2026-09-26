package ai.medhaleak.ocrprocessor.testing;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** A PNG of what a non-browser case executed and validated, used when the case has no page to capture. */
final class EvidenceCard {

    private static final int WIDTH = 640;
    private static final int MARGIN = 32;
    private static final int LINE = 22;

    private EvidenceCard() {
    }

    static void writeIfAbsent(Path file, String name, String status, String started, String ended, String observation) {
        if (file == null || Files.isRegularFile(file)) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            BufferedImage image = draw(name, status, started, ended, observation);
            Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
            ImageIO.write(image, "png", tmp.toFile());
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) {
            // The row still shows the written evidence when the image cannot be saved.
        }
    }

    private static BufferedImage draw(String name, String status, String started, String ended, String observation) {
        List<String> lines = new ArrayList<>();
        lines.add("Started  " + blank(started));
        lines.add("Ended    " + blank(ended));
        EvidenceParts parts = EvidenceParts.parse(observation);
        if (!parts.executed.isBlank()) {
            lines.add("");
            lines.add("Executed");
            wrap(lines, parts.executed);
        }
        if (!parts.validated.isBlank()) {
            lines.add("");
            lines.add("Validated");
            wrap(lines, parts.validated);
        }
        if (!parts.observed.isBlank()) {
            lines.add("");
            lines.add(parts.executed.isBlank() ? "Result" : "Observed");
            wrap(lines, parts.observed);
        }
        int height = 96 + lines.size() * LINE + MARGIN;
        BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, height);
        boolean failed = "FAILED".equals(status) || "ERROR".equals(status);
        g.setColor(failed ? new Color(0xFD, 0xE8, 0xE8) : new Color(0xDA, 0xFB, 0xE1));
        g.fillRect(0, 0, WIDTH, 72);
        g.setColor(failed ? new Color(0x9B, 0x1C, 0x1C) : new Color(0x11, 0x63, 0x29));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 20));
        g.drawString(trim(name, 70), MARGIN, 32);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.drawString(status == null ? "" : status, MARGIN, 56);
        g.setColor(new Color(0x1F, 0x23, 0x28));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        int y = 100;
        for (String line : lines) {
            if (line.equals("Executed") || line.equals("Validated") || line.equals("Observed") || line.equals("Result")) {
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
                g.setColor(new Color(0x57, 0x60, 0x6A));
            } else {
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
                g.setColor(new Color(0x1F, 0x23, 0x28));
            }
            g.drawString(line, MARGIN, y);
            y += LINE;
        }
        g.dispose();
        return image;
    }

    private static void wrap(List<String> lines, String text) {
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (current.length() + word.length() + 1 > 58) {
                lines.add(current.toString());
                current = new StringBuilder();
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(word);
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String trim(String value, int max) {
        if (value == null || value.isBlank()) {
            return "Test case";
        }
        return value.length() <= max ? value : value.substring(0, max) + "…";
    }

    record EvidenceParts(String executed, String validated, String observed) {
        static EvidenceParts parse(String observation) {
            String text = observation == null ? "" : observation.trim();
            if (!text.startsWith("Executed:")) {
                return new EvidenceParts("", "", text);
            }
            int validatedAt = text.indexOf(" Validated:");
            int observedAt = text.indexOf(" Observed:");
            String executed = slice(text, "Executed:".length(), validatedAt);
            String validated = validatedAt < 0 ? "" : slice(text, validatedAt + " Validated:".length(), observedAt);
            String observed = observedAt < 0 ? "" : text.substring(observedAt + " Observed:".length()).trim();
            return new EvidenceParts(stripPeriod(executed), stripPeriod(validated), stripPeriod(observed));
        }

        private static String slice(String text, int start, int end) {
            if (end < start) {
                end = text.length();
            }
            return text.substring(start, end).trim();
        }

        private static String stripPeriod(String value) {
            String trimmed = value.trim();
            return trimmed.endsWith(".") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
        }
    }
}
