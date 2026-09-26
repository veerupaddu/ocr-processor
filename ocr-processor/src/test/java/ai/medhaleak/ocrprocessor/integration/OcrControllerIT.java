package ai.medhaleak.ocrprocessor.integration;

import ai.medhaleak.ocrprocessor.exception.OcrExtractionException;
import ai.medhaleak.ocrprocessor.service.LlmService;
import ai.medhaleak.ocrprocessor.service.OcrEngine;
import ai.medhaleak.ocrprocessor.testing.Evidence;
import io.restassured.response.Response;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class OcrControllerIT {

    @LocalServerPort int port;
    @MockBean LlmService llmService;
    @MockBean OcrEngine ocrEngine;

    private String jwt;

    @BeforeEach
    void setUp() throws Exception {
        RestAssured.port = port;
        RestAssured.basePath = "";
        jwt = registerAndLogin("ocruser_" + System.nanoTime(), "password123");
    }

    // ── Upload ────────────────────────────────────────────────────────────────

    @Test
    void upload_validPng_returnsCreated() throws Exception {
        when(ocrEngine.extract(any(), anyString())).thenReturn("Extracted OCR text");
        when(llmService.summarise(anyString())).thenReturn("A summary of the document");

        Response response = given()
            .cookie("jwt", jwt)
            .multiPart("file", "test.png", new byte[512], "image/png")
            .formParam("documentName", "My Test Doc")
        .when()
            .post("/api/v1/ocr/upload")
        .then()
            .extract().response();
        Evidence.record(
                "Signed in as the record owner and uploaded test.png with the document name \"My Test Doc\". OCR and the summary model were stubbed so the HTTP result is the thing under test",
                "The upload must be accepted and stored as a completed record. The server returned HTTP "
                        + response.statusCode() + " with status " + response.path("status")
                        + ", id " + response.path("id")
                        + ", extracted text \"" + response.path("extractedText")
                        + "\", and summary \"" + response.path("summary") + "\"");
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.path("status").toString()).isEqualTo("COMPLETE");
        assertThat(response.path("extractedText").toString()).isEqualTo("Extracted OCR text");
        assertThat(response.path("summary").toString()).isEqualTo("A summary of the document");
        assertThat(response.path("id").toString()).isNotBlank();
    }

    @Test
    void upload_ocrFails_returnsFailed() throws Exception {
        when(ocrEngine.extract(any(), anyString()))
            .thenThrow(new OcrExtractionException("fail", new RuntimeException()));

        Response response = given()
            .cookie("jwt", jwt)
            .multiPart("file", "fail.png", new byte[512], "image/png")
        .when()
            .post("/api/v1/ocr/upload")
        .then()
            .extract().response();
        Evidence.record("file=fail.png engine throws OcrExtractionException \"fail\"",
                "HTTP " + response.statusCode() + " record " + response.path("status"),
                "id=" + response.path("id") + " extractedText=" + response.path("extractedText"));
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.path("status").toString()).isEqualTo("FAILED");
    }

    @Test
    void upload_unsupportedType_returnsBadRequest() {
        Response response = given()
            .cookie("jwt", jwt)
            .multiPart("file", "doc.docx", new byte[512],
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        .when()
            .post("/api/v1/ocr/upload")
        .then()
            .extract().response();

        Evidence.record("file=doc.docx type=application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "HTTP " + response.statusCode(), "message=" + response.path("message"));
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.path("message").toString()).contains("Unsupported");
    }

    @Test
    void upload_withoutAuth_redirectsToLogin() {
        Response response = given()
            .redirects().follow(false)
            .multiPart("file", "test.png", new byte[512], "image/png")
        .when()
            .post("/api/v1/ocr/upload")
        .then()
            .extract().response();

        Evidence.record("file=test.png cookie=(none)", "HTTP " + response.statusCode(),
                "Location=" + response.getHeader("Location"));
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.getHeader("Location")).contains("/login");
    }

    // ── Search ────────────────────────────────────────────────────────────────

    @Test
    void search_emptyQuery_returnsPagedResults() throws Exception {
        when(ocrEngine.extract(any(), anyString())).thenReturn("invoice payment");
        when(llmService.summarise(anyString())).thenReturn("summary");

        // upload a record first
        given()
            .cookie("jwt", jwt)
            .multiPart("file", "invoice.png", new byte[512], "image/png")
            .formParam("documentName", "Invoice 2024")
        .when()
            .post("/api/v1/ocr/upload");

        Response response = given()
            .cookie("jwt", jwt)
            .queryParam("q", "")
            .queryParam("page", 0)
            .queryParam("size", 20)
        .when()
            .get("/api/v1/ocr/search")
        .then()
            .extract().response();
        Evidence.record("q=\"\" page=0 size=20 documentName=\"Invoice 2024\"",
                "HTTP " + response.statusCode(),
                "totalElements=" + response.path("totalElements")
                        + " first=" + response.path("content[0].documentName"));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat((Integer) response.path("totalElements")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void search_withQuery_filtersResults() throws Exception {
        when(ocrEngine.extract(any(), anyString())).thenReturn("unique-search-term-xyz");
        when(llmService.summarise(anyString())).thenReturn("summary");

        given()
            .cookie("jwt", jwt)
            .multiPart("file", "unique.png", new byte[512], "image/png")
            .formParam("documentName", "Unique Doc")
        .when()
            .post("/api/v1/ocr/upload");

        Response response = given()
            .cookie("jwt", jwt)
            .queryParam("q", "unique-search-term-xyz")
        .when()
            .get("/api/v1/ocr/search")
        .then()
            .extract().response();
        Evidence.record("q=\"unique-search-term-xyz\" documentName=\"Unique Doc\"",
                "HTTP " + response.statusCode(),
                "totalElements=" + response.path("totalElements")
                        + " first=" + response.path("content[0].documentName"));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat((Integer) response.path("totalElements")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void search_noResults_returnsEmptyPage() {
        Response response = given()
            .cookie("jwt", jwt)
            .queryParam("q", "this-term-will-never-match-xyzxyz")
        .when()
            .get("/api/v1/ocr/search")
        .then()
            .extract().response();
        Evidence.record("q=\"this-term-will-never-match-xyzxyz\"",
                "HTTP " + response.statusCode(),
                "totalElements=" + response.path("totalElements") + " content=" + response.path("content"));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat((Integer) response.path("totalElements")).isZero();
        assertThat((java.util.List<?>) response.path("content")).isEmpty();
    }

    // ── Detail ────────────────────────────────────────────────────────────────

    @Test
    void getById_existingRecord_returnsFullDetail() throws Exception {
        when(ocrEngine.extract(any(), anyString())).thenReturn("full text content");
        when(llmService.summarise(anyString())).thenReturn("full summary");

        String id = given()
            .cookie("jwt", jwt)
            .multiPart("file", "detail.png", new byte[512], "image/png")
            .formParam("documentName", "Detail Test")
        .when()
            .post("/api/v1/ocr/upload")
        .then()
            .extract().path("id");

        Response response = given()
            .cookie("jwt", jwt)
        .when()
            .get("/api/v1/ocr/" + id)
        .then()
            .extract().response();
        Evidence.record("id=" + id + " file=detail.png",
                "HTTP " + response.statusCode(),
                "extractedText=\"" + response.path("extractedText") + "\" summary=\"" + response.path("summary")
                        + "\" originalFilename=" + response.path("originalFilename"));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.path("id").toString()).isEqualTo(id);
        assertThat(response.path("extractedText").toString()).isEqualTo("full text content");
        assertThat(response.path("summary").toString()).isEqualTo("full summary");
        assertThat(response.path("originalFilename").toString()).isEqualTo("detail.png");
    }

    @Test
    void getById_notFound_returns404() {
        Response response = given()
            .cookie("jwt", jwt)
        .when()
            .get("/api/v1/ocr/00000000-0000-0000-0000-000000000000")
        .then()
            .extract().response();
        Evidence.record("id=00000000-0000-0000-0000-000000000000",
                "HTTP " + response.statusCode(), "message=" + response.path("message"));
        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    void getById_otherUsersRecord_returns404() throws Exception {
        // Upload as user1
        when(ocrEngine.extract(any(), anyString())).thenReturn("private text");
        when(llmService.summarise(anyString())).thenReturn("summary");

        String id = given()
            .cookie("jwt", jwt)
            .multiPart("file", "private.png", new byte[512], "image/png")
        .when()
            .post("/api/v1/ocr/upload")
        .then()
            .extract().path("id");

        // Try to access as user2
        String jwt2 = registerAndLogin("otheruser_" + System.nanoTime(), "password123");
        Response response = given()
            .cookie("jwt", jwt2)
        .when()
            .get("/api/v1/ocr/" + id)
        .then()
            .extract().response();
        Evidence.record("owner A id=" + id + " requested with owner B cookie",
                "HTTP " + response.statusCode(), "message=" + response.path("message"));
        assertThat(response.statusCode()).isEqualTo(404);
    }

    // ── Retry Summary ─────────────────────────────────────────────────────────

    @Test
    void retrySummary_failedRecord_returnsUpdatedSummary() throws Exception {
        when(ocrEngine.extract(any(), anyString())).thenReturn("text to summarise");
        when(llmService.summarise(anyString()))
            .thenThrow(new ai.medhaleak.ocrprocessor.exception.LlmUnavailableException("down", new RuntimeException()))
            .thenReturn("retry summary");

        String id = given()
            .cookie("jwt", jwt)
            .multiPart("file", "retry.png", new byte[512], "image/png")
        .when()
            .post("/api/v1/ocr/upload")
        .then()
            .extract().path("id");

        Response response = given()
            .cookie("jwt", jwt)
        .when()
            .post("/api/v1/ocr/" + id + "/retry-summary")
        .then()
            .extract().response();
        Evidence.record("id=" + id + " first summary failed, retry text=\"text to summarise\"",
                "HTTP " + response.statusCode() + " summary " + response.path("summaryStatus"),
                "summary=\"" + response.path("summary") + "\"");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.path("summary").toString()).isEqualTo("retry summary");
        assertThat(response.path("summaryStatus").toString()).isEqualTo("COMPLETE");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String registerAndLogin(String username, String password) {
        String email = username + "@example.com";
        CsrfClient.register(username, email, password);

        return CsrfClient.login(username, password);
    }
}
