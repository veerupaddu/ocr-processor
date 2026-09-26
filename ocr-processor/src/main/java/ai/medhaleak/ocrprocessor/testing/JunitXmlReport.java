package ai.medhaleak.ocrprocessor.testing;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reads a JUnit XML report into one row per test case. */
public final class JunitXmlReport {

    public record Row(String className, String method, String status, long durationMs, String observation,
                      String failureBody) {
        public Row(String className, String method, String status, long durationMs, String observation) {
            this(className, method, status, durationMs, observation, "");
        }
    }

    private JunitXmlReport() {
    }

    public static List<Row> parse(Path xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        try {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (IllegalArgumentException ignored) {
            // Some parsers reject these attributes. Secure processing is still on.
        }
        factory.setExpandEntityReferences(false);
        Document document = factory.newDocumentBuilder().parse(xml.toFile());
        NodeList nodes = document.getElementsByTagName("testcase");
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            rows.add(toRow(element));
        }
        return rows;
    }

    public static List<Row> parseDirectory(Path directory) throws Exception {
        List<Row> rows = new ArrayList<>();
        if (!Files.isDirectory(directory)) {
            return rows;
        }
        try (var stream = Files.list(directory)) {
            List<Path> files = stream
                    .filter(path -> path.getFileName().toString().startsWith("TEST-") && path.toString().endsWith(".xml"))
                    .sorted()
                    .toList();
            for (Path file : files) {
                rows.addAll(parse(file));
            }
        }
        return rows;
    }

    private static Row toRow(Element element) {
        String className = element.getAttribute("classname");
        String method = element.getAttribute("name");
        long durationMs = secondsToMillis(element.getAttribute("time"));
        Element failure = firstChild(element, "failure");
        Element error = firstChild(element, "error");
        Element skipped = firstChild(element, "skipped");
        if (failure != null) {
            return new Row(className, method, "FAILED", durationMs, message(failure), body(failure));
        }
        if (error != null) {
            return new Row(className, method, "ERROR", durationMs, message(error), body(error));
        }
        if (skipped != null) {
            return new Row(className, method, "SKIPPED", durationMs, message(skipped));
        }
        String evidence = evidence(element);
        return new Row(className, method, "PASSED", durationMs, evidence.isBlank() ? "Passed" : evidence);
    }

    private static String evidence(Element element) {
        NodeList nodes = element.getElementsByTagName("system-out");
        StringBuilder facts = new StringBuilder();
        for (int i = 0; i < nodes.getLength(); i++) {
            String text = nodes.item(i).getTextContent();
            if (text == null) {
                continue;
            }
            for (String line : text.split("\\R")) {
                int marker = line.indexOf("EVIDENCE:");
                if (marker < 0) {
                    continue;
                }
                String fact = line.substring(marker + "EVIDENCE:".length()).trim();
                if (fact.isBlank()) {
                    continue;
                }
                if (facts.length() > 0) {
                    facts.append(" | ");
                }
                facts.append(fact);
            }
        }
        String collapsed = facts.toString().replaceAll("\\s+", " ").trim();
        if (collapsed.length() > 900) {
            return collapsed.substring(0, 900) + "…";
        }
        return collapsed;
    }

    private static Element firstChild(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        if (nodes.getLength() == 0) {
            return null;
        }
        return (Element) nodes.item(0);
    }

    private static String body(Element element) {
        String text = element.getTextContent();
        if (text == null) {
            return "";
        }
        if (text.length() > 8000) {
            return text.substring(0, 8000);
        }
        return text;
    }

    private static String message(Element element) {
        String message = element.getAttribute("message");
        String text = message == null || message.isBlank() ? element.getTextContent() : message;
        if (text == null) {
            return "";
        }
        String collapsed = text.replaceAll("\\s+", " ").trim();
        if (collapsed.length() > 900) {
            return collapsed.substring(0, 900) + "…";
        }
        return collapsed;
    }

    private static long secondsToMillis(String seconds) {
        if (seconds == null || seconds.isBlank()) {
            return 0;
        }
        return Math.round(Double.parseDouble(seconds) * 1000);
    }
}
