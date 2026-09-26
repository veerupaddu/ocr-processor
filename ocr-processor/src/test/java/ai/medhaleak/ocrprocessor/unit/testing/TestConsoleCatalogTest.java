package ai.medhaleak.ocrprocessor.unit.testing;

import ai.medhaleak.ocrprocessor.testing.FailureExplanation;
import ai.medhaleak.ocrprocessor.testing.JunitXmlReport;
import ai.medhaleak.ocrprocessor.testing.TestConsoleService;
import ai.medhaleak.ocrprocessor.testing.TestConsoleService.Catalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestConsoleCatalogTest {

    @Test
    void catalogListsTheThreeSuites() throws Exception {
        Catalog catalog = new ObjectMapper().readValue(
                getClass().getResourceAsStream("/test-catalog.json"), Catalog.class);

        assertEquals(3, catalog.suites().size());
        assertEquals(32, suite(catalog, "unit").cases().size());
        assertEquals(29, suite(catalog, "integration").cases().size());
        assertEquals(40, suite(catalog, "e2e").cases().size());

        Set<String> ids = new HashSet<>();
        for (Catalog.Suite suite : catalog.suites()) {
            for (Catalog.Case testCase : suite.cases()) {
                assertTrue(testCase.expected() != null && !testCase.expected().isBlank(), testCase.id());
                assertTrue(ids.add(suite.id() + ":" + testCase.id()), testCase.id());
            }
        }
    }

    @Test
    void reportParserKeepsTimeAndFailureMessage() throws Exception {
        Path xml = Path.of("src/test/resources/junit-sample.xml");
        var rows = JunitXmlReport.parse(xml);

        assertEquals(2, rows.size());
        assertEquals("PASSED", rows.get(0).status());
        assertEquals(42, rows.get(0).durationMs());
        assertEquals("parsed user id 11111111-1111-1111-1111-111111111111", rows.get(0).observation());
        assertEquals("FAILED", rows.get(1).status());
        assertEquals(1500, rows.get(1).durationMs());
        assertTrue(rows.get(1).observation().contains("expected login redirect"));
    }

    @Test
    void reportRowMatchesCatalogIdByClassSuffix() throws Exception {
        Catalog catalog = new ObjectMapper().readValue(
                getClass().getResourceAsStream("/test-catalog.json"), Catalog.class);
        Catalog.Suite e2e = suite(catalog, "e2e");
        JunitXmlReport.Row row = new JunitXmlReport.Row(
                "e2e.test_auth.TestRegistration",
                "test_register_success_redirects",
                "PASSED",
                10,
                "Passed");

        Catalog.Case matched = TestConsoleService.match(e2e, row);
        assertEquals("TestRegistration.test_register_success_redirects", matched.id());
    }

    @Test
    void selectedUnitCasesBecomeSurefireMethods() {
        String filter = TestConsoleService.mavenTests("unit", List.of(
                "JwtUtilTest.generateAndParse_roundtrip",
                "JwtUtilTest.parseToken_invalidToken_throwsJwtException",
                "LlmServiceTest.summarise_success_returnsContent"));

        assertEquals("ai.medhaleak.ocrprocessor.unit.service.JwtUtilTest#generateAndParse_roundtrip+parseToken_invalidToken_throwsJwtException,"
                + "ai.medhaleak.ocrprocessor.unit.service.LlmServiceTest#summarise_success_returnsContent", filter);
    }

    @Test
    void selectedE2eCasesBecomePytestNodes() {
        List<String> nodes = TestConsoleService.pytestNodes(List.of(
                "test_ui.test_home_without_login_redirects",
                "TestLogin.test_login_unknown_user_returns_error"));

        assertEquals(List.of(
                "tests/e2e/selenium/test_ui.py::test_home_without_login_redirects",
                "tests/e2e/test_auth.py::TestLogin::test_login_unknown_user_returns_error"), nodes);
    }

    @Test
    void failureDetailsNameTheLineTheReasonAndTheFix() throws Exception {
        Path root = Files.createTempDirectory("failure-detail");
        Path file = root.resolve("src/test/java/demo/Sample.java");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                class Sample {
                    void check() {
                        assertThat(password.length()).isGreaterThanOrEqualTo(8);
                    }
                }
                """);
        String body = """
                Expecting actual:
                  3
                to be greater than or equal to:
                  8
                \tat demo.Sample.check(Sample.java:3)
                """;

        FailureExplanation.Detail detail = FailureExplanation.explain(
                "short password", body, root, "A password shorter than 8 characters is rejected.");

        assertTrue(detail.where().contains("src/test/java/demo/Sample.java:3"));
        assertTrue(detail.where().contains("isGreaterThanOrEqualTo(8)"));
        assertTrue(detail.why().contains("at least 8"));
        assertTrue(detail.why().contains("3"));
        assertTrue(detail.fix().contains("at least 8"));
        assertTrue(detail.fix().contains("shorter than 8 characters is rejected"));
    }

    @Test
    void failureDetailsExplainTheSampleCases() {
        Path project = Path.of("").toAbsolutePath();
        FailureExplanation.Detail password = FailureExplanation.explain("", """
                java.lang.AssertionError:
                Expecting actual:
                  3
                to be greater than or equal to:
                  8
                \tat ai.medhaleak.ocrprocessor.unit.testing.FailureSamplesTest.shortPassword_isAccepted(FailureSamplesTest.java:17)
                """, project, "A password shorter than 8 characters is rejected.");
        assertTrue(password.where().contains("FailureSamplesTest.java:17"));
        assertTrue(password.where().contains("isGreaterThanOrEqualTo(8)"));
        assertTrue(password.fix().contains("is 3"));
        assertTrue(password.fix().contains("at least 8"));

        FailureExplanation.Detail upload = FailureExplanation.explain("", """
                Expecting ListN:
                  ["pdf", "png", "jpg", "jpeg", "tiff"]
                to contain:
                  ["docx"]
                \tat ai.medhaleak.ocrprocessor.unit.testing.FailureSamplesTest.docxUpload_isStored(FailureSamplesTest.java:23)
                """, project, "A docx file is rejected.");
        assertTrue(upload.why().contains("docx is not in pdf, png, jpg, jpeg, tiff"));
        assertTrue(upload.fix().contains("Use one of pdf, png, jpg, jpeg, tiff"));

        FailureExplanation.Detail retry = FailureExplanation.explain("", """
                expected: "FAILED"
                 but was: "COMPLETE"
                \tat ai.medhaleak.ocrprocessor.unit.testing.FailureSamplesTest.retryWhileComplete_updatesSummary(FailureSamplesTest.java:29)
                """, project, "Retry is refused unless the summary status is FAILED.");
        assertTrue(retry.why().contains("required FAILED"));
        assertTrue(retry.why().contains("COMPLETE"));
        assertTrue(retry.fix().contains("was COMPLETE"));
        assertTrue(retry.fix().contains("so it is FAILED"));
    }

    @Test
    void screenshotFileStaysInsideTheScreenshotsDirectory() throws Exception {
        Path root = Files.createTempDirectory("shots");
        Path dir = root.resolve("target/test-screenshots");
        Files.createDirectories(dir);
        Files.write(dir.resolve("test_ui.login.png"), new byte[] {(byte) 137, 80, 78, 71});

        assertEquals(dir.resolve("test_ui.login.png"), TestConsoleService.screenshotFile(root, "test_ui.login"));
        assertEquals("/api/v1/tests/screenshots/test_ui.login", TestConsoleService.screenshotUrl(root, "test_ui.login"));
        assertTrue(TestConsoleService.screenshotFile(root, "../pom") == null);
        assertTrue(TestConsoleService.screenshotFile(root, "missing.case") == null);
    }

    @Test
    void unknownSelectedCaseIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> TestConsoleService.mavenTests("unit", List.of("Nope.missing")));
        assertThrows(IllegalArgumentException.class,
                () -> TestConsoleService.pytestNodes(List.of("Nope.missing")));
    }

    private static Catalog.Suite suite(Catalog catalog, String id) {
        return catalog.suites().stream().filter(suite -> suite.id().equals(id)).findFirst().orElseThrow();
    }
}
