package ai.medhaleak.ocrprocessor.testing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class TestConsoleService {

    private static final String UNIT_TESTS = String.join(",",
            "ai.medhaleak.ocrprocessor.unit.service.JwtUtilTest",
            "ai.medhaleak.ocrprocessor.unit.service.LlmServiceTest",
            "ai.medhaleak.ocrprocessor.unit.service.OcrServiceTest",
            "ai.medhaleak.ocrprocessor.unit.service.UserServiceTest",
            "ai.medhaleak.ocrprocessor.unit.service.TesseractOcrEngineTest",
            "ai.medhaleak.ocrprocessor.service.TesseractOcrEngineReadabilityTest",
            "ai.medhaleak.ocrprocessor.unit.testing.FailureSamplesTest");

    private static final String INTEGRATION_TESTS = String.join(",",
            "ai.medhaleak.ocrprocessor.integration.ApplicationContextIT",
            "ai.medhaleak.ocrprocessor.integration.AuthControllerIT",
            "ai.medhaleak.ocrprocessor.integration.OcrControllerIT",
            "ai.medhaleak.ocrprocessor.integration.OcrRecordRepositoryIT");

    private static final Map<String, String> E2E_TARGETS = Map.ofEntries(
            Map.entry("test_ui", "tests/e2e/selenium/test_ui.py"),
            Map.entry("TestRegistration", "tests/e2e/test_auth.py::TestRegistration"),
            Map.entry("TestLogin", "tests/e2e/test_auth.py::TestLogin"),
            Map.entry("TestLogout", "tests/e2e/test_auth.py::TestLogout"),
            Map.entry("TestChangePassword", "tests/e2e/test_auth.py::TestChangePassword"),
            Map.entry("TestOcrUpload", "tests/e2e/test_ocr.py::TestOcrUpload"),
            Map.entry("TestOcrDetail", "tests/e2e/test_ocr.py::TestOcrDetail"),
            Map.entry("TestSearch", "tests/e2e/test_search.py::TestSearch"),
            Map.entry("TestSummary", "tests/e2e/test_summary.py::TestSummary"));

    private final ObjectMapper objectMapper;
    private final int port;
    private final Catalog catalog;
    private final Map<String, SuiteState> states = new LinkedHashMap<>();
    private final Object runLock = new Object();
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private final AtomicReference<String> active = new AtomicReference<>();
    private Pending running;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "test-console");
        thread.setDaemon(true);
        return thread;
    });

    public TestConsoleService(ObjectMapper objectMapper, @Value("${server.port:8080}") int port) {
        this.objectMapper = objectMapper;
        this.port = port;
        this.catalog = loadCatalog();
        for (Catalog.Suite suite : catalog.suites()) {
            states.put(suite.id(), new SuiteState());
        }
    }

    public Map<String, Object> snapshot() {
        List<Map<String, Object>> suites = new ArrayList<>();
        for (Catalog.Suite suite : catalog.suites()) {
            suites.add(suiteView(suite, states.get(suite.id())));
        }
        String runningId = active.get();
        List<String> queued;
        synchronized (runLock) {
            queued = new ArrayList<>();
            for (Pending job : pending) {
                queued.add(job.suiteId());
            }
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("runningSuite", runningId == null ? "" : runningId);
        view.put("queued", queued);
        view.put("suites", suites);
        return view;
    }

    public void start(String suiteId) {
        start(suiteId, null);
    }

    public void start(String suiteId, List<String> caseIds) {
        Catalog.Suite suite = catalog.suites().stream()
                .filter(candidate -> candidate.id().equals(suiteId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown suite: " + suiteId));
        List<String> requested = resolveCases(suite, caseIds);
        Pending job = new Pending(suiteId, requested);
        boolean launch;
        synchronized (runLock) {
            if (running != null) {
                pending.add(job);
                launch = false;
            } else {
                running = job;
                active.set(suiteId);
                launch = true;
            }
        }
        if (launch) {
            executor.submit(this::drain);
        }
    }

    private void drain() {
        while (true) {
            Pending job;
            synchronized (runLock) {
                job = running;
            }
            SuiteState state = states.get(job.suiteId());
            try {
                prepare(job);
                runSuite(job.suiteId());
            } catch (Exception ex) {
                state.status = "FAILED";
                state.observation = ex.getMessage() == null ? ex.toString() : ex.getMessage();
            } finally {
                state.running = false;
            }
            synchronized (runLock) {
                Pending next = pending.poll();
                if (next == null) {
                    running = null;
                    active.set(null);
                    return;
                }
                running = next;
                active.set(next.suiteId());
            }
        }
    }

    private void prepare(Pending job) {
        Catalog.Suite suite = catalog.suites().stream()
                .filter(candidate -> candidate.id().equals(job.suiteId()))
                .findFirst()
                .orElseThrow();
        SuiteState state = states.get(job.suiteId());
        state.running = true;
        state.status = "RUNNING";
        state.requested = job.requested();
        if (job.requested() == null) {
            state.outcomes.clear();
            state.observation = "Running…";
        } else {
            for (String id : job.requested()) {
                state.outcomes.put(id, new Outcome("RUNNING", -1, "Running", "", "", "", System.currentTimeMillis(), 0));
            }
            state.observation = summary(state, suite);
        }
    }

    private static List<String> resolveCases(Catalog.Suite suite, List<String> caseIds) {
        if (caseIds == null) {
            return null;
        }
        Set<String> known = new LinkedHashSet<>();
        for (Catalog.Case testCase : suite.cases()) {
            known.add(testCase.id());
        }
        List<String> selected = new ArrayList<>();
        for (String id : caseIds) {
            if (id == null || !known.contains(id)) {
                throw new IllegalArgumentException("Unknown case: " + id);
            }
            if (!selected.contains(id)) {
                selected.add(id);
            }
        }
        if (selected.isEmpty()) {
            throw new IllegalArgumentException("Select at least one case.");
        }
        if (selected.size() == suite.cases().size()) {
            return null;
        }
        return List.copyOf(selected);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private void runSuite(String suiteId) throws Exception {
        SuiteState state = states.get(suiteId);
        Path project = projectRoot();
        Path reportPath = reportPath(project, suiteId);
        clearReports(project, suiteId);
        clearScreenshots(project, suiteId);
        List<String> command = command(project, suiteId, state.requested);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(project.toFile());
        builder.redirectErrorStream(true);
        // The app process exports the real database. Tests must keep the test profile datasource.
        builder.environment().remove("SPRING_DATASOURCE_URL");
        builder.environment().remove("SPRING_DATASOURCE_USERNAME");
        builder.environment().remove("SPRING_DATASOURCE_PASSWORD");
        builder.environment().remove("SPRING_DATASOURCE_DRIVER_CLASS_NAME");
        Process process = builder.start();
        Path progress = project.resolve("target/test-progress.log");
        int[] seen = {0};
        Catalog.Suite suite = catalog.suites().stream().filter(s -> s.id().equals(suiteId)).findFirst().orElseThrow();
        Thread watcher = new Thread(() -> followProgress(progress, suite, state, process, seen), "test-console-progress");
        watcher.setDaemon(true);
        watcher.start();
        StringBuilder output = new StringBuilder();
        Thread reader = new Thread(() -> appendOutput(process, output), "test-console-output");
        reader.setDaemon(true);
        reader.start();
        boolean finished = process.waitFor(timeout(suiteId).toSeconds(), java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            reader.join(2_000);
            state.status = "FAILED";
            state.observation = "The suite timed out after " + timeout(suiteId).toMinutes() + " minutes.";
            return;
        }
        reader.join(5_000);
        watcher.join(2_000);
        List<JunitXmlReport.Row> rows = readRows(project, suiteId, reportPath);
        applyRows(catalog.suites().stream().filter(s -> s.id().equals(suiteId)).findFirst().orElseThrow(), state, rows, project);
        int exit = process.exitValue();
        if ("RUNNING".equals(state.status)) {
            state.status = exit == 0 ? "PASSED" : "FAILED";
        }
        if (rows.isEmpty()) {
            state.status = "FAILED";
            state.observation = tail(output.toString(), exit);
        } else if (exit != 0 && "PASSED".equals(state.status)) {
            state.status = "FAILED";
            state.observation = tail(output.toString(), exit);
        } else if (state.observation == null || "Running…".equals(state.observation)) {
            state.observation = summary(state, catalog.suites().stream().filter(s -> s.id().equals(suiteId)).findFirst().orElseThrow());
        }
    }

    private void applyRows(Catalog.Suite suite, SuiteState state, List<JunitXmlReport.Row> rows, Path project) {
        Set<String> seen = new LinkedHashSet<>();
        boolean partial = partial(state, suite);
        for (JunitXmlReport.Row row : rows) {
            Catalog.Case matched = match(suite, row);
            if (matched == null) {
                continue;
            }
            if (partial && !state.requested.contains(matched.id())) {
                continue;
            }
            FailureExplanation.Detail detail = failureDetail(row, project, matched.expected());
            Outcome previous = state.outcomes.get(matched.id());
            Outcome outcome = new Outcome(
                    row.status(), row.durationMs(), row.observation(), detail.where(), detail.why(), detail.fix(),
                    previous == null ? 0 : previous.startedAt, previous == null ? 0 : previous.endedAt);
            state.outcomes.put(matched.id(), outcome);
            ensureShot(project, matched, outcome);
            seen.add(matched.id());
        }
        List<String> scope = scope(state, suite);
        if (partial) {
            for (String id : scope) {
                if (!seen.contains(id)) {
                    state.outcomes.remove(id);
                }
            }
        }
        boolean bad = false;
        for (String id : scope) {
            Outcome outcome = state.outcomes.get(id);
            if (outcome == null || !"PASSED".equals(outcome.status)) {
                bad = true;
                break;
            }
        }
        state.status = bad ? "FAILED" : "PASSED";
        state.observation = summary(state, suite);
    }

    private static List<String> scope(SuiteState state, Catalog.Suite suite) {
        if (partial(state, suite)) {
            return state.requested;
        }
        List<String> ids = new ArrayList<>();
        for (Catalog.Case testCase : suite.cases()) {
            ids.add(testCase.id());
        }
        return ids;
    }

    private static boolean partial(SuiteState state, Catalog.Suite suite) {
        return state.requested != null && state.requested.size() < suite.cases().size();
    }

    private static String summary(SuiteState state, Catalog.Suite suite) {
        List<String> scope = scope(state, suite);
        long passed = 0;
        long failed = 0;
        long running = 0;
        long present = 0;
        for (String id : scope) {
            Outcome outcome = state.outcomes.get(id);
            if (outcome == null) {
                continue;
            }
            present++;
            if ("PASSED".equals(outcome.status)) {
                passed++;
            } else if ("FAILED".equals(outcome.status) || "ERROR".equals(outcome.status)) {
                failed++;
            } else if ("RUNNING".equals(outcome.status)) {
                running++;
            }
        }
        long missing = scope.size() - present;
        String prefix = partial(state, suite) ? scope.size() + " selected: " : "";
        if (running > 0) {
            return prefix + passed + " passed, " + failed + " failed, " + running + " running.";
        }
        return prefix + passed + " passed, " + failed + " failed, " + missing + " not run.";
    }

    private void followProgress(Path file, Catalog.Suite suite, SuiteState state, Process process, int[] seen) {
        while (process.isAlive()) {
            readProgress(file, suite, state, seen);
            try {
                Thread.sleep(400);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        readProgress(file, suite, state, seen);
    }

    private void readProgress(Path file, Catalog.Suite suite, SuiteState state, int[] seen) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(file);
            boolean changed = false;
            for (int i = seen[0]; i < lines.size(); i++) {
                if (applyProgressLine(projectRoot(), suite, state, lines.get(i))) {
                    changed = true;
                }
            }
            seen[0] = lines.size();
            if (changed && state.running) {
                state.observation = summary(state, suite);
            }
        } catch (java.io.IOException ignored) {
            // The file is still being written.
        }
    }

    private static boolean applyProgressLine(Path project, Catalog.Suite suite, SuiteState state, String line) {
        String[] parts = line.split("\t", 6);
        if (parts.length < 4) {
            return false;
        }
        String id = parts[0];
        Catalog.Case testCase = suite.cases().stream().filter(candidate -> candidate.id().equals(id)).findFirst().orElse(null);
        if (testCase == null) {
            return false;
        }
        long duration = Long.parseLong(parts[2]);
        long started = parts.length >= 6 ? Long.parseLong(parts[3]) : 0;
        long ended = parts.length >= 6 ? Long.parseLong(parts[4]) : 0;
        String observation = parts.length >= 6 ? parts[5] : parts[3];
        Outcome previous = state.outcomes.get(id);
        if (started <= 0 && previous != null) {
            started = previous.startedAt;
        }
        Outcome outcome = new Outcome(parts[1], duration, observation, "", "", "", started, ended);
        state.outcomes.put(id, outcome);
        if (!"RUNNING".equals(outcome.status)) {
            ensureShot(project, testCase, outcome);
        }
        return true;
    }

    public static Catalog.Case match(Catalog.Suite suite, JunitXmlReport.Row row) {
        String method = row.method();
        int bracket = method.indexOf('[');
        if (bracket > 0) {
            method = method.substring(0, bracket);
        }
        List<Catalog.Case> hits = new ArrayList<>();
        for (Catalog.Case testCase : suite.cases()) {
            int dot = testCase.id().lastIndexOf('.');
            if (dot < 0) {
                continue;
            }
            String classPart = testCase.id().substring(0, dot);
            String methodPart = testCase.id().substring(dot + 1);
            if (!methodPart.equals(method)) {
                continue;
            }
            String className = row.className() == null ? "" : row.className();
            if (className.equals(classPart) || className.endsWith("." + classPart)) {
                hits.add(testCase);
            }
        }
        if (hits.size() == 1) {
            return hits.get(0);
        }
        return null;
    }

    private List<String> command(Path project, String suiteId, List<String> requested) {
        if ("e2e".equals(suiteId)) {
            Path pytest = project.resolve("tests/.venv/bin/pytest");
            String binary = Files.isExecutable(pytest) ? pytest.toString() : "pytest";
            List<String> command = new ArrayList<>();
            command.add(binary);
            if (requested == null) {
                command.add("tests/e2e/selenium");
                command.add("tests/e2e/test_auth.py");
                command.add("tests/e2e/test_ocr.py");
                command.add("tests/e2e/test_search.py");
                command.add("tests/e2e/test_summary.py");
            } else {
                command.addAll(pytestNodes(requested));
            }
            command.add("--junitxml=target/e2e-junit.xml");
            command.add("-o");
            command.add("junit_logging=system-out");
            command.add("--base-url=http://127.0.0.1:" + port);
            command.add("-q");
            return command;
        }
        String tests = requested == null
                ? ("unit".equals(suiteId) ? UNIT_TESTS : INTEGRATION_TESTS)
                : mavenTests(suiteId, requested);
        return List.of(mavenBinary(), "-q", "test", "-Dtest=" + tests, "-DfailIfNoTests=false");
    }

    public static String mavenTests(String suiteId, List<String> caseIds) {
        String classes = "unit".equals(suiteId) ? UNIT_TESTS : INTEGRATION_TESTS;
        Map<String, String> bySimpleName = new LinkedHashMap<>();
        for (String className : classes.split(",")) {
            bySimpleName.put(className.substring(className.lastIndexOf('.') + 1), className);
        }
        Map<String, List<String>> methods = new LinkedHashMap<>();
        for (String id : caseIds) {
            int dot = id == null ? -1 : id.lastIndexOf('.');
            if (dot < 1) {
                throw new IllegalArgumentException("Unknown case: " + id);
            }
            String className = bySimpleName.get(id.substring(0, dot));
            if (className == null) {
                throw new IllegalArgumentException("Unknown case: " + id);
            }
            methods.computeIfAbsent(className, key -> new ArrayList<>()).add(id.substring(dot + 1));
        }
        List<String> filters = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : methods.entrySet()) {
            filters.add(entry.getKey() + "#" + String.join("+", entry.getValue()));
        }
        return String.join(",", filters);
    }

    public static List<String> pytestNodes(List<String> caseIds) {
        List<String> nodes = new ArrayList<>();
        for (String id : caseIds) {
            int dot = id == null ? -1 : id.lastIndexOf('.');
            if (dot < 1) {
                throw new IllegalArgumentException("Unknown case: " + id);
            }
            String target = E2E_TARGETS.get(id.substring(0, dot));
            if (target == null) {
                throw new IllegalArgumentException("Unknown case: " + id);
            }
            nodes.add(target + "::" + id.substring(dot + 1));
        }
        return nodes;
    }

    private static Duration timeout(String suiteId) {
        return switch (suiteId) {
            case "integration" -> Duration.ofMinutes(20);
            case "e2e" -> Duration.ofMinutes(15);
            default -> Duration.ofMinutes(8);
        };
    }

    private static Path reportPath(Path project, String suiteId) {
        if ("e2e".equals(suiteId)) {
            return project.resolve("target/e2e-junit.xml");
        }
        return project.resolve("target/surefire-reports");
    }

    private static List<JunitXmlReport.Row> readRows(Path project, String suiteId, Path reportPath) throws Exception {
        if ("e2e".equals(suiteId)) {
            if (!Files.isRegularFile(reportPath)) {
                return List.of();
            }
            return JunitXmlReport.parse(reportPath);
        }
        return JunitXmlReport.parseDirectory(project.resolve("target/surefire-reports"));
    }

    private static void clearReports(Path project, String suiteId) throws Exception {
        Files.deleteIfExists(project.resolve("target/test-progress.log"));
        if ("e2e".equals(suiteId)) {
            Files.deleteIfExists(project.resolve("target/e2e-junit.xml"));
            return;
        }
        Path reports = project.resolve("target/surefire-reports");
        if (!Files.isDirectory(reports)) {
            return;
        }
        try (var stream = Files.list(reports)) {
            for (Path file : stream.filter(path -> path.getFileName().toString().endsWith(".xml")).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }

    private static String mavenBinary() {
        String home = System.getenv("MAVEN_HOME");
        if (home != null && !home.isBlank()) {
            Path bin = Path.of(home, "bin", "mvn");
            if (Files.isExecutable(bin)) {
                return bin.toString();
            }
        }
        for (String candidate : List.of("/opt/homebrew/bin/mvn", "/usr/local/bin/mvn")) {
            if (Files.isExecutable(Path.of(candidate))) {
                return candidate;
            }
        }
        return "mvn";
    }

    static Path projectRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("pom.xml")) && Files.exists(dir.resolve("src/main/resources/test-catalog.json"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("Could not find the ocr-processor project. Start the app from that directory.");
    }

    private Catalog loadCatalog() {
        try (InputStream in = getClass().getResourceAsStream("/test-catalog.json")) {
            if (in == null) {
                throw new IllegalStateException("test-catalog.json is missing from the classpath.");
            }
            return objectMapper.readValue(in, Catalog.class);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Could not read test-catalog.json", ex);
        }
    }

    private static void appendOutput(Process process, StringBuilder buffer) {
        try (InputStream in = process.getInputStream()) {
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) >= 0) {
                buffer.append(new String(chunk, 0, read, StandardCharsets.UTF_8));
                if (buffer.length() > 200_000) {
                    buffer.delete(0, buffer.length() - 100_000);
                }
            }
        } catch (Exception ignored) {
            // The process was stopped, or the stream closed.
        }
    }

    private static String tail(String output, int exit) {
        String trimmed = output == null ? "" : output.trim();
        if (trimmed.length() > 500) {
            trimmed = trimmed.substring(trimmed.length() - 500);
        }
        if (trimmed.isBlank()) {
            return "The runner exited with code " + exit + " and wrote no test report.";
        }
        return "The runner exited with code " + exit + ". " + trimmed.replaceAll("\\s+", " ");
    }

    private Map<String, Object> suiteView(Catalog.Suite suite, SuiteState state) {
        List<Map<String, Object>> cases = new ArrayList<>();
        int passed = 0;
        int failed = 0;
        for (Catalog.Case testCase : suite.cases()) {
            Outcome outcome = state.outcomes.get(testCase.id());
            String status = outcome == null ? "NOT_RUN" : outcome.status;
            String observation = outcome == null ? "Not run in this session." : outcome.observation;
            Long duration = outcome == null || outcome.durationMs < 0 ? null : outcome.durationMs;
            if ("PASSED".equals(status)) {
                passed++;
            } else if ("FAILED".equals(status) || "ERROR".equals(status)) {
                failed++;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", testCase.id());
            row.put("name", testCase.name());
            row.put("expected", testCase.expected());
            row.put("status", status);
            row.put("durationMs", duration);
            row.put("started", outcome == null ? "" : clock(outcome.startedAt));
            row.put("ended", outcome == null ? "" : clock(outcome.endedAt));
            row.put("observation", observation);
            row.put("screenshot", outcome == null ? "" : screenshotUrl(projectRoot(), testCase.id()));
            row.put("where", outcome == null ? "" : outcome.where);
            row.put("why", outcome == null ? "" : outcome.why);
            row.put("fix", outcome == null ? "" : outcome.fix);
            cases.add(row);
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", suite.id());
        view.put("title", suite.title());
        view.put("description", suite.description() == null ? "" : suite.description());
        view.put("status", state.status);
        view.put("running", state.running);
        view.put("observation", state.observation);
        view.put("passed", passed);
        view.put("failed", failed);
        view.put("total", suite.cases().size());
        view.put("cases", cases);
        return view;
    }

    public record Catalog(List<Suite> suites) {
        public record Suite(String id, String title, String description, List<Case> cases) {
        }

        public record Case(String id, String name, String expected) {
        }
    }

    private static final class SuiteState {
        volatile String status = "IDLE";
        volatile String observation = "Not run in this session.";
        volatile boolean running;
        volatile List<String> requested;
        final Map<String, Outcome> outcomes = new java.util.concurrent.ConcurrentHashMap<>();
    }

    private static FailureExplanation.Detail failureDetail(JunitXmlReport.Row row, Path project, String rule) {
        if (!"FAILED".equals(row.status()) && !"ERROR".equals(row.status())) {
            return new FailureExplanation.Detail("", "", "");
        }
        return FailureExplanation.explain(row.observation(), row.failureBody(), project, rule);
    }

    private record Outcome(String status, long durationMs, String observation, String where, String why, String fix,
                           long startedAt, long endedAt) {
    }

    private static void ensureShot(Path project, Catalog.Case testCase, Outcome outcome) {
        Path dir = project.resolve("target/test-screenshots");
        Path file = dir.resolve(testCase.id() + ".png").normalize();
        if (!file.startsWith(dir.normalize())) {
            return;
        }
        EvidenceCard.writeIfAbsent(file, testCase.name(), outcome.status, clock(outcome.startedAt), clock(outcome.endedAt),
                outcome.observation);
    }

    private static String clock(long epochMs) {
        if (epochMs <= 0) {
            return "";
        }
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(java.time.ZoneId.systemDefault())
                .format(java.time.Instant.ofEpochMilli(epochMs));
    }

    public byte[] readScreenshot(String caseId) {
        try {
            Path file = screenshotFile(projectRoot(), caseId);
            return file == null ? null : Files.readAllBytes(file);
        } catch (Exception ex) {
            return null;
        }
    }

    /** PNG saved by a browser case, or null when this case has no screenshot. */
    public static Path screenshotFile(Path project, String caseId) {
        if (caseId == null || !caseId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,180}")) {
            return null;
        }
        Path dir = project.resolve("target/test-screenshots").normalize();
        Path file = dir.resolve(caseId + ".png").normalize();
        if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
            return null;
        }
        return file;
    }

    public static String screenshotUrl(Path project, String caseId) {
        return screenshotFile(project, caseId) == null ? "" : "/api/v1/tests/screenshots/" + caseId;
    }

    private void clearScreenshots(Path project, String suiteId) throws Exception {
        Catalog.Suite suite = catalog.suites().stream().filter(s -> s.id().equals(suiteId)).findFirst().orElse(null);
        if (suite == null) {
            return;
        }
        SuiteState state = states.get(suiteId);
        List<Catalog.Case> cases = suite.cases();
        for (Catalog.Case testCase : cases) {
            if (state.requested != null && !state.requested.contains(testCase.id())) {
                continue;
            }
            Path file = screenshotFile(project, testCase.id());
            if (file != null) {
                Files.deleteIfExists(file);
            }
        }
    }

    private record Pending(String suiteId, List<String> requested) {
    }
}
