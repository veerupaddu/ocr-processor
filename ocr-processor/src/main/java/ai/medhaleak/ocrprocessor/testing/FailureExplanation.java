package ai.medhaleak.ocrprocessor.testing;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns a JUnit failure into where it failed, why, and how to fix it. */
public final class FailureExplanation {

    public record Detail(String where, String why, String fix) {
    }

    private static final Pattern JAVA_FRAME = Pattern.compile("at ([\\w.$]+)\\(([\\w.$]+):(\\d+)\\)");
    private static final Pattern PYTHON_FRAME = Pattern.compile("([\\w./-]+\\.py):(\\d+)");

    private FailureExplanation() {
    }

    public static Detail explain(String message, String body, Path project, String rule) {
        String trace = body == null ? "" : body;
        List<String> lines = new ArrayList<>();
        for (String line : trace.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        Location location = locate(lines, project);
        Comparison comparison = comparison(lines);
        String why = why(message, lines, comparison);
        String where = location.text;
        String fix = fix(comparison, location, why, rule);
        return new Detail(where, why, fix);
    }

    private static String why(String message, List<String> lines, Comparison comparison) {
        if (comparison != null) {
            return comparison.why;
        }
        if (message != null && !message.isBlank()) {
            return message.replaceAll("\\s+", " ").trim();
        }
        for (String line : lines) {
            if (line.startsWith("at ") || line.startsWith("Expecting")) {
                continue;
            }
            return line;
        }
        return "The case failed.";
    }

    private static String fix(Comparison comparison, Location location, String why, String rule) {
        String place = location.place;
        String action;
        if (comparison == null) {
            action = "The check at " + place + " failed: " + why + ".";
        } else {
            action = switch (comparison.kind) {
                case AT_LEAST -> "The value at " + place + " is " + comparison.actual
                        + ". Change it so it is at least " + comparison.expected + ".";
                case EQUAL -> "The result at " + place + " was " + comparison.actual
                        + ". Change it so it is " + comparison.expected + ".";
                case CONTAINS -> comparison.actual + " is not accepted at " + place
                        + ". Use one of " + comparison.expected + ".";
            };
        }
        if (rule == null || rule.isBlank()) {
            return action;
        }
        return action + " " + rule.trim();
    }

    private static Comparison comparison(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("expected:") && i + 1 < lines.size() && lines.get(i + 1).startsWith("but was:")) {
                String expected = clean(line.substring("expected:".length()));
                String actual = clean(lines.get(i + 1).substring("but was:".length()));
                return new Comparison(Kind.EQUAL, expected, actual,
                        "The case required " + expected + " but the run produced " + actual + ".");
            }
            if (line.startsWith("to be equal to:") && i > 0 && i + 1 < lines.size()) {
                String expected = clean(lines.get(i + 1));
                String actual = clean(previousValue(lines, i));
                return new Comparison(Kind.EQUAL, expected, actual,
                        "The case required " + expected + " but the run produced " + actual + ".");
            }
            if (line.startsWith("to be greater than or equal to:") && i > 0 && i + 1 < lines.size()) {
                String expected = clean(lines.get(i + 1));
                String actual = clean(previousValue(lines, i));
                return new Comparison(Kind.AT_LEAST, expected, actual,
                        "The case required at least " + expected + " but the value was " + actual + ".");
            }
            if (line.startsWith("to contain:") && i > 0 && i + 1 < lines.size()) {
                String expected = clean(previousValue(lines, i));
                String actual = clean(lines.get(i + 1));
                return new Comparison(Kind.CONTAINS, expected, actual,
                        actual + " is not in " + expected + ".");
            }
        }
        return null;
    }

    private static String previousValue(List<String> lines, int verbIndex) {
        for (int i = verbIndex - 1; i >= 0; i--) {
            String line = lines.get(i);
            if (line.startsWith("Expecting") || line.startsWith("at ")) {
                continue;
            }
            return line;
        }
        return lines.get(verbIndex - 1);
    }

    private static String clean(String value) {
        String text = value.trim();
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1);
        }
        if (text.startsWith("[") && text.endsWith("]")) {
            text = text.substring(1, text.length() - 1).replace("\"", "");
        }
        return text;
    }

    private static Location locate(List<String> lines, Path project) {
        for (String line : lines) {
            Matcher java = JAVA_FRAME.matcher(line);
            if (java.find() && java.group(1).startsWith("ai.medhaleak.")) {
                return location(project, java.group(1), java.group(2), Integer.parseInt(java.group(3)));
            }
        }
        for (String line : lines) {
            Matcher python = PYTHON_FRAME.matcher(line);
            if (python.find()) {
                return fileLocation(project, Path.of(python.group(1)), Integer.parseInt(python.group(2)), python.group(1) + ":" + python.group(2));
            }
        }
        for (String line : lines) {
            Matcher java = JAVA_FRAME.matcher(line);
            if (java.find()) {
                return location(project, java.group(1), java.group(2), Integer.parseInt(java.group(3)));
            }
        }
        return new Location("The report did not include a source line.", "this case");
    }

    private static Location location(Path project, String className, String fileName, int lineNumber) {
        int methodDot = className.lastIndexOf('.');
        String fqcn = methodDot > 0 ? className.substring(0, methodDot) : className;
        if (fqcn.contains("$")) {
            fqcn = fqcn.substring(0, fqcn.indexOf('$'));
        }
        String relative = fqcn.replace('.', '/') + ".java";
        Path testFile = project.resolve("src/test/java").resolve(relative);
        Path mainFile = project.resolve("src/main/java").resolve(relative);
        Path file = Files.isRegularFile(testFile) ? testFile : mainFile;
        String label = fileName + ":" + lineNumber;
        return fileLocation(project, file, lineNumber, label);
    }

    private static Location fileLocation(Path project, Path file, int lineNumber, String label) {
        String source = sourceLine(file, lineNumber);
        String display = file.toString();
        Path absoluteProject = project.toAbsolutePath().normalize();
        Path absoluteFile = file.toAbsolutePath().normalize();
        if (absoluteFile.startsWith(absoluteProject)) {
            display = absoluteProject.relativize(absoluteFile).toString();
        }
        String place = display + ":" + lineNumber;
        String text = source.isBlank() ? place : place + "\n" + source;
        if (text.equals(place) && !label.equals(place)) {
            text = label;
        }
        return new Location(text, place);
    }

    private static String sourceLine(Path file, int lineNumber) {
        if (lineNumber < 1 || !Files.isRegularFile(file)) {
            return "";
        }
        try {
            List<String> lines = Files.readAllLines(file);
            if (lineNumber > lines.size()) {
                return "";
            }
            return lines.get(lineNumber - 1).trim();
        } catch (Exception ex) {
            return "";
        }
    }

    private enum Kind {
        EQUAL, AT_LEAST, CONTAINS
    }

    private record Comparison(Kind kind, String expected, String actual, String why) {
    }

    private record Location(String text, String place) {
    }
}
