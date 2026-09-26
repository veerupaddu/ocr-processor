package ai.medhaleak.ocrprocessor.testing;

import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Writes one line per test so the home-page console can update that row while Maven is still running. */
public class TestProgressExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

    private static final Path FILE = Path.of("target", "test-progress.log");
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(TestProgressExtension.class);

    @Override
    public void beforeTestExecution(ExtensionContext context) {
        Evidence.clear();
        long startedAt = System.currentTimeMillis();
        context.getStore(NAMESPACE).put("start", System.nanoTime());
        context.getStore(NAMESPACE).put("startedAt", startedAt);
        write(caseId(context), "RUNNING", -1, startedAt, 0, "Running");
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        Long start = context.getStore(NAMESPACE).remove("start", Long.class);
        Long startedAt = context.getStore(NAMESPACE).remove("startedAt", Long.class);
        long durationMs = start == null ? 0 : Math.max(0, (System.nanoTime() - start) / 1_000_000);
        long endedAt = System.currentTimeMillis();
        long began = startedAt == null ? endedAt : startedAt;
        if (context.getExecutionException().isPresent()) {
            Throwable error = context.getExecutionException().orElseThrow();
            String message = error.getMessage() == null ? error.toString() : error.getMessage();
            write(caseId(context), "FAILED", durationMs, began, endedAt, message);
        } else {
            String evidence = Evidence.take();
            write(caseId(context), "PASSED", durationMs, began, endedAt, evidence.isBlank() ? "Passed" : evidence);
        }
    }

    private static String caseId(ExtensionContext context) {
        return context.getRequiredTestClass().getSimpleName() + "." + context.getRequiredTestMethod().getName();
    }

    private static void write(String id, String status, long durationMs, long startedAt, long endedAt, String observation) {
        String safe = observation == null ? "" : observation.replaceAll("\\s+", " ").trim();
        if (safe.length() > 900) {
            safe = safe.substring(0, 900) + "…";
        }
        String line = id + "\t" + status + "\t" + durationMs + "\t" + startedAt + "\t" + endedAt + "\t" + safe + "\n";
        try {
            Files.createDirectories(FILE.getParent());
            synchronized (TestProgressExtension.class) {
                Files.writeString(FILE, line, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (Exception ignored) {
            // A missing progress line still leaves the final JUnit report.
        }
    }
}
