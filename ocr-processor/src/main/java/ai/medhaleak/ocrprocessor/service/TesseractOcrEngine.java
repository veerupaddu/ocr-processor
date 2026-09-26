package ai.medhaleak.ocrprocessor.service;

import ai.medhaleak.ocrprocessor.exception.OcrExtractionException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.ocr.TesseractOCRParser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class TesseractOcrEngine implements OcrEngine {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcrEngine.class);

    private static final int OCR_TIMEOUT_SECONDS = 120;
    private static final double MIN_WORD_CONFIDENCE = 60;
    private static final Set<String> SHORT_WORDS = Set.of(
            "a", "i", "ai", "al", "of", "to", "in", "on", "or", "is", "be", "by",
            "an", "as", "at", "we", "it", "do", "if", "no", "so", "up", "for");

    private final TesseractOCRParser ocrParser = new TesseractOCRParser();
    private final Path tesseractBinary;
    private final String tessdataPath;
    private final String language;
    private final Path magickBinary;

    public TesseractOcrEngine(
            @Value("${app.ocr.tessdata-path:}") String configuredTessdata,
            @Value("${app.ocr.language:eng}") String language) {
        this.language = language;
        this.tesseractBinary = resolveTesseractBinary();
        if (tesseractBinary == null) {
            throw new IllegalStateException(
                    "tesseract executable not found. Install tesseract-ocr and ensure it is on PATH.");
        }
        this.tessdataPath = resolveTessdata(configuredTessdata);
        this.magickBinary = resolveMagickBinary();
        ocrParser.setTesseractPath(tesseractBinary.getParent().toString());
        if (tessdataPath != null) {
            ocrParser.setTessdataPath(tessdataPath);
        }
        if (magickBinary != null) {
            ocrParser.setImageMagickPath(magickBinary.getParent().toString());
            ocrParser.setEnableImagePreprocessing(true);
        }
        ocrParser.setLanguage(language);
        ocrParser.setPageSegMode("3");
        ocrParser.setPreserveInterwordSpacing(true);
        ocrParser.setApplyRotation(true);
        ocrParser.setTimeout(OCR_TIMEOUT_SECONDS);
        try {
            ocrParser.initialize(Map.of());
        } catch (TikaException e) {
            throw new IllegalStateException("Failed to initialize Tesseract", e);
        }
        log.info("Tesseract binary: {} datapath: {} magick: {}", tesseractBinary, tessdataPath, magickBinary);
    }

    @Override
    public String extract(byte[] fileBytes, String mimeType) throws OcrExtractionException {
        try {
            if (mimeType != null && mimeType.startsWith("image/")) {
                return extractImage(fileBytes, mimeType);
            }
            return extractDocument(fileBytes, mimeType);
        } catch (OcrExtractionException e) {
            throw e;
        } catch (Exception e) {
            log.error("OCR extraction failed for mimeType={}", mimeType, e);
            throw new OcrExtractionException("OCR extraction failed", e);
        }
    }

    private String extractImage(byte[] fileBytes, String mimeType) throws OcrExtractionException {
        try {
            return doExtractImage(fileBytes, mimeType, false);
        } catch (Exception primaryEx) {
            log.warn("Direct OCR extraction failed for mimeType={}, trying ImageIO normalized PNG: {}",
                    mimeType, primaryEx.getMessage());
            try {
                return doExtractImage(fileBytes, mimeType, true);
            } catch (Exception normEx) {
                log.warn("Normalized OCR extraction failed, falling back to Tika parser: {}", normEx.getMessage());
                try {
                    return parseWithTika(fileBytes, true);
                } catch (Exception tikaEx) {
                    log.error("All OCR extraction strategies failed for mimeType={}", mimeType, tikaEx);
                    throw new OcrExtractionException("OCR extraction failed", primaryEx);
                }
            }
        }
    }

    private String doExtractImage(byte[] fileBytes, String mimeType, boolean normalizeRgb) throws Exception {
        Path input = null;
        Path processed = null;
        try {
            if (normalizeRgb) {
                input = normalizeToRgbPng(fileBytes);
            }
            if (input == null) {
                input = Files.createTempFile("ocr-in-", suffixFor(mimeType));
                Files.write(input, fileBytes);
            }
            processed = preprocess(input);
            Path source = processed != null ? processed : input;

            String page = null;
            try {
                page = readableFromTsv(runTesseract(source, "3", "tsv"));
            } catch (Exception ex) {
                log.debug("Tesseract PSM 3 TSV failed: {}", ex.getMessage());
            }

            String sparse = null;
            try {
                sparse = readableFromTsv(runTesseract(source, "11", "tsv"));
            } catch (Exception ex) {
                log.debug("Tesseract PSM 11 TSV failed: {}", ex.getMessage());
            }

            if (page != null || sparse != null) {
                if (page == null) return sparse;
                if (sparse == null) return page;
                return letterCount(page) >= letterCount(sparse) ? page : sparse;
            }

            // Fallback to standard non-TSV text extraction if TSV segmentation failed
            try {
                String plain = runTesseract(source, "3", null);
                if (plain != null && !plain.isBlank()) {
                    return plain.trim();
                }
            } catch (Exception ex) {
                log.debug("Tesseract PSM 3 plain text failed: {}", ex.getMessage());
            }

            try {
                String plain6 = runTesseract(source, "6", null);
                if (plain6 != null && !plain6.isBlank()) {
                    return plain6.trim();
                }
            } catch (Exception ex) {
                log.debug("Tesseract PSM 6 plain text failed: {}", ex.getMessage());
            }

            return "";
        } finally {
            deleteQuietly(processed);
            deleteQuietly(input);
        }
    }

    private static Path normalizeToRgbPng(byte[] fileBytes) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(fileBytes));
            if (img == null) {
                return null;
            }
            BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(img, 0, 0, null);
            g.dispose();
            Path out = Files.createTempFile("ocr-norm-", ".png");
            ImageIO.write(rgb, "png", out.toFile());
            return out;
        } catch (Exception e) {
            log.debug("Image normalization skipped: {}", e.getMessage());
            return null;
        }
    }

    private Path preprocess(Path input) {
        if (magickBinary == null) {
            return null;
        }
        Path output = null;
        try {
            output = Files.createTempFile("ocr-pre-", ".png");
            Process process = new ProcessBuilder(
                    magickBinary.toString(),
                    input.toString(),
                    "-colorspace", "Gray",
                    "-resize", "200%",
                    "-normalize",
                    "-sharpen", "0x0.8",
                    output.toString())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0 || Files.size(output) == 0) {
                process.destroyForcibly();
                deleteQuietly(output);
                return null;
            }
            return output;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Image preprocessing skipped", e);
            deleteQuietly(output);
            return null;
        }
    }

    /**
     * Turns Tesseract TSV into lines of text, dropping glyphs it is not sure about.
     * Icons and background art show up as low-confidence symbols and short fragments.
     */
    static String readableFromTsv(String tsv) {
        Map<String, List<String>> lines = new LinkedHashMap<>();
        for (String row : tsv.split("\\R")) {
            String[] cols = row.split("\t", -1);
            if (cols.length < 12 || !"5".equals(cols[0])) {
                continue;
            }
            double confidence;
            try {
                confidence = Double.parseDouble(cols[10]);
            } catch (NumberFormatException e) {
                continue;
            }
            String word = cleanWord(cols[11]);
            if (!keepWord(word, confidence)) {
                continue;
            }
            String lineKey = cols[1] + ":" + cols[2] + ":" + cols[3] + ":" + cols[4];
            lines.computeIfAbsent(lineKey, key -> new ArrayList<>()).add(word);
        }
        StringBuilder text = new StringBuilder();
        for (List<String> words : lines.values()) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(String.join(" ", words));
        }
        return text.toString().trim();
    }

    static boolean keepWord(String word, double confidence) {
        if (word.isBlank() || confidence < MIN_WORD_CONFIDENCE) {
            return false;
        }
        String letters = word.replaceAll("[^A-Za-z]", "");
        if (letters.length() >= 3) {
            return true;
        }
        return letters.length() == 2
                && SHORT_WORDS.contains(letters.toLowerCase(Locale.ROOT))
                && confidence >= 75;
    }

    private static String cleanWord(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim()
                .replaceAll("^[^A-Za-z0-9]+", "")
                .replaceAll("[^A-Za-z0-9]+$", "");
    }

    private static int letterCount(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetter(text.charAt(i))) {
                count++;
            }
        }
        return count;
    }

    private String runTesseract(Path image, String pageSegMode, String outputFormat)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(tesseractBinary.toString());
        command.add(image.toAbsolutePath().normalize().toString());
        command.add("stdout");
        command.add("-l");
        command.add(language);
        if (pageSegMode != null && !pageSegMode.isBlank()) {
            command.add("--psm");
            command.add(pageSegMode);
        }
        if (tessdataPath != null) {
            command.add("--tessdata-dir");
            command.add(tessdataPath);
        }
        if (outputFormat != null && !outputFormat.isBlank()) {
            command.add(outputFormat);
        }
        Process process = new ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(OCR_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("tesseract timed out: " + stderr);
        }
        if (process.exitValue() != 0) {
            if (output != null && !output.isBlank()) {
                log.warn("Tesseract exited with status {} but produced output: {}", process.exitValue(), stderr.trim());
                return output;
            }
            throw new IOException("tesseract exited with status " + process.exitValue() + ": " + stderr.trim());
        }
        return output == null ? "" : output;
    }

    private String extractDocument(byte[] fileBytes, String mimeType) throws IOException, TikaException, SAXException {
        String embedded = parseWithTika(fileBytes, false);
        if (!"application/pdf".equals(mimeType) || embedded.length() >= 200) {
            return embedded;
        }
        String ocr = parseWithTika(fileBytes, true);
        return ocr.length() > embedded.length() ? ocr : embedded;
    }

    private String parseWithTika(byte[] fileBytes, boolean ocrPdf) throws IOException, TikaException, SAXException {
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        AutoDetectParser parser = new AutoDetectParser();
        ParseContext context = new ParseContext();
        context.set(Parser.class, parser);
        if (ocrPdf) {
            TesseractOCRConfig config = new TesseractOCRConfig();
            config.setLanguage(language);
            config.setPageSegMode("3");
            config.setTimeoutSeconds(OCR_TIMEOUT_SECONDS);
            config.setPreserveInterwordSpacing(true);
            config.setEnableImagePreprocessing(magickBinary != null);
            context.set(TesseractOCRConfig.class, config);
            PDFParserConfig pdfConfig = new PDFParserConfig();
            pdfConfig.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.OCR_ONLY);
            pdfConfig.setOcrDPI(300);
            context.set(PDFParserConfig.class, pdfConfig);
        }
        try (InputStream is = new ByteArrayInputStream(fileBytes)) {
            parser.parse(is, handler, metadata, context);
        }
        return handler.toString().trim();
    }

    private static String suffixFor(String mimeType) {
        if (mimeType == null) {
            return ".png";
        }
        return switch (mimeType.toLowerCase(Locale.ROOT)) {
            case "image/png", "image/x-png" -> ".png";
            case "image/jpeg", "image/jpg", "image/pjpeg" -> ".jpg";
            case "image/tiff", "image/x-tiff" -> ".tif";
            case "image/bmp", "image/x-ms-bmp" -> ".bmp";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".png";
        };
    }

    private static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not delete temp OCR file {}", path, e);
        }
    }

    static Path resolveMagickBinary() {
        return firstExecutable("magick", "convert",
                "/opt/homebrew/bin/magick",
                "/usr/local/bin/magick",
                "/usr/bin/convert");
    }

    static Path resolveTesseractBinary() {
        return firstExecutable("tesseract", null,
                "/opt/homebrew/bin/tesseract",
                "/usr/local/bin/tesseract",
                "/usr/bin/tesseract");
    }

    private static Path firstExecutable(String pathName, String alternateName, String... absolute) {
        List<Path> candidates = new ArrayList<>();
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(java.io.File.pathSeparator)) {
                if (dir.isBlank()) {
                    continue;
                }
                candidates.add(Path.of(dir, pathName));
                if (alternateName != null) {
                    candidates.add(Path.of(dir, alternateName));
                }
            }
        }
        for (String candidate : absolute) {
            candidates.add(Path.of(candidate));
        }
        for (Path candidate : candidates) {
            if (Files.isExecutable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    static String resolveTessdata(String configured) {
        List<Path> candidates = new ArrayList<>();
        addCandidate(candidates, configured);
        String env = System.getenv("TESSDATA_PREFIX");
        addCandidate(candidates, env);
        if (env != null && !env.isBlank() && !env.endsWith("tessdata")) {
            addCandidate(candidates, env.replaceAll("/+$", "") + "/tessdata");
        }
        for (String path : List.of(
                "/opt/homebrew/share/tessdata",
                "/usr/local/share/tessdata",
                "/usr/share/tesseract-ocr/5/tessdata",
                "/usr/share/tesseract-ocr/4.00/tessdata",
                "/usr/share/tessdata")) {
            addCandidate(candidates, path);
        }
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate.resolve("eng.traineddata"))) {
                return candidate.toString();
            }
        }
        return null;
    }

    private static void addCandidate(List<Path> candidates, String path) {
        if (path == null || path.isBlank()) {
            return;
        }
        candidates.add(Path.of(path.trim()));
    }
}
