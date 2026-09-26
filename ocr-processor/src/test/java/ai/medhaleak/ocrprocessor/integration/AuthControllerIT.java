package ai.medhaleak.ocrprocessor.integration;

import ai.medhaleak.ocrprocessor.service.LlmService;
import ai.medhaleak.ocrprocessor.service.OcrEngine;
import ai.medhaleak.ocrprocessor.testing.Evidence;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class AuthControllerIT {

    @LocalServerPort int port;
    @MockBean LlmService llmService;
    @MockBean OcrEngine ocrEngine;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        RestAssured.basePath = "";
    }

    // ── Registration ──────────────────────────────────────────────────────────

    @Test
    void register_validRequest_redirectsToLogin() {
        Response response = CsrfClient.form("/register")
            .redirects().follow(false)
            .contentType(ContentType.URLENC)
            .formParam("username", "testuser1")
            .formParam("email", "testuser1@example.com")
            .formParam("password", "password123")
            .formParam("confirmPassword", "password123")
        .when()
            .post("/register")
        .then()
            .extract().response();

        Evidence.record("username=testuser1 email=testuser1@example.com password=password123",
                "HTTP " + response.statusCode(), "Location=" + response.getHeader("Location"));
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.getHeader("Location")).contains("/login");
    }

    @Test
    void register_duplicateUsername_returnsError() {
        // Register once
        registerUser("dupuser", "dup@example.com", "password123");

        // Register again with same username
        Response response = CsrfClient.form("/register")
            .contentType(ContentType.URLENC)
            .formParam("username", "dupuser")
            .formParam("email", "other@example.com")
            .formParam("password", "password123")
            .formParam("confirmPassword", "password123")
        .when()
            .post("/register")
        .then()
            .extract().response();
        String page = visible(response);
        Evidence.record("username=dupuser email=other@example.com password=password123",
                "HTTP " + response.statusCode(), page);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(page).contains("already taken");
    }

    @Test
    void register_passwordMismatch_returnsValidationError() {
        Response response = CsrfClient.form("/register")
            .contentType(ContentType.URLENC)
            .formParam("username", "mismatchuser")
            .formParam("email", "mismatch@example.com")
            .formParam("password", "password123")
            .formParam("confirmPassword", "different456")
        .when()
            .post("/register")
        .then()
            .extract().response();
        String page = visible(response);
        Evidence.record("username=mismatchuser password=password123 confirm=different456",
                "HTTP " + response.statusCode(), page);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(page).contains("Passwords do not match");
    }

    @Test
    void register_shortPassword_returnsValidationError() {
        Response response = CsrfClient.form("/register")
            .contentType(ContentType.URLENC)
            .formParam("username", "shortpwduser")
            .formParam("email", "short@example.com")
            .formParam("password", "abc")
            .formParam("confirmPassword", "abc")
        .when()
            .post("/register")
        .then()
            .extract().response();
        String page = visible(response);
        Evidence.record("username=shortpwduser password=abc", "HTTP " + response.statusCode(), page);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(page).contains("8");
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Test
    void login_validCredentials_setsJwtCookie() {
        registerUser("loginuser", "login@example.com", "password123");

        Response response = CsrfClient.form("/login")
            .redirects().follow(false)
            .contentType(ContentType.URLENC)
            .formParam("username", "loginuser")
            .formParam("password", "password123")
        .when()
            .post("/login")
        .then()
            .extract().response();

        String jwt = response.getCookie("jwt");
        Evidence.record("username=loginuser password=password123",
                "HTTP " + response.statusCode(),
                "Location=" + response.getHeader("Location") + " jwt=" + (jwt == null ? "(none)" : jwt.substring(0, Math.min(24, jwt.length())) + "…"));
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.getHeader("Location")).contains("/home");
        assertThat(jwt).isNotBlank();
    }

    @Test
    void login_invalidPassword_returnsError() {
        registerUser("badpwduser", "badpwd@example.com", "password123");

        Response response = CsrfClient.form("/login")
            .contentType(ContentType.URLENC)
            .formParam("username", "badpwduser")
            .formParam("password", "wrongpassword")
        .when()
            .post("/login")
        .then()
            .extract().response();
        String page = visible(response);
        Evidence.record("username=badpwduser password=wrongpassword", "HTTP " + response.statusCode(), page);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(page).contains("Invalid");
    }

    @Test
    void login_unknownUser_returnsError() {
        Response response = CsrfClient.form("/login")
            .contentType(ContentType.URLENC)
            .formParam("username", "nobody")
            .formParam("password", "password123")
        .when()
            .post("/login")
        .then()
            .extract().response();
        String page = visible(response);
        Evidence.record("username=nobody password=password123", "HTTP " + response.statusCode(), page);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(page).contains("Invalid");
    }

    @Test
    void protectedRoute_withoutToken_redirectsToLogin() {
        Response response = given()
            .redirects().follow(false)
        .when()
            .get("/home")
        .then()
            .extract().response();

        Evidence.record("GET /home cookie=(none)", "HTTP " + response.statusCode(),
                "Location=" + response.getHeader("Location"));
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.getHeader("Location")).contains("/login");
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @Test
    void logout_clearsCookie() {
        String jwt = loginAndGetToken("logoutuser", "logout@example.com", "password123");

        Response response = given()
            .cookie("jwt", jwt)
            .redirects().follow(false)
        .when()
            .post("/logout")
        .then()
            .extract().response();
        Evidence.record("cookie jwt length=" + jwt.length(), "HTTP " + response.statusCode(),
                "Location=" + response.getHeader("Location") + " jwt=" + response.getCookie("jwt"));
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.getCookie("jwt")).isNullOrEmpty();
    }

    // ── Change Password ───────────────────────────────────────────────────────

    @Test
    void changePassword_success() {
        String jwt = loginAndGetToken("changepwduser", "changepwd@example.com", "oldpassword1");

        Response response = CsrfClient.form("/profile/change-password", Map.of("jwt", jwt))
            .redirects().follow(false)
            .contentType(ContentType.URLENC)
            .formParam("currentPassword", "oldpassword1")
            .formParam("newPassword", "newpassword2")
            .formParam("confirmPassword", "newpassword2")
        .when()
            .post("/profile/change-password")
        .then()
            .extract().response();

        String relogin = CsrfClient.login("changepwduser", "newpassword2");
        Evidence.record("username=changepwduser current=oldpassword1 new=newpassword2",
                "HTTP " + response.statusCode(),
                "Location=" + response.getHeader("Location")
                        + " new-login jwt=" + (relogin == null ? "(none)" : relogin.substring(0, Math.min(24, relogin.length())) + "…"));
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.getHeader("Location")).contains("/profile/change-password");
        assertThat(relogin).isNotBlank();
    }

    @Test
    void changePassword_wrongCurrentPassword_returnsError() {
        String jwt = loginAndGetToken("changepwduser2", "changepwd2@example.com", "oldpassword1");

        Response response = CsrfClient.form("/profile/change-password", Map.of("jwt", jwt))
            .contentType(ContentType.URLENC)
            .formParam("currentPassword", "wrongpassword")
            .formParam("newPassword", "newpassword2")
            .formParam("confirmPassword", "newpassword2")
        .when()
            .post("/profile/change-password")
        .then()
            .extract().response();
        String page = visible(response);
        Evidence.record("username=changepwduser2 current=wrongpassword new=newpassword2",
                "HTTP " + response.statusCode(), page);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(page).contains("incorrect");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String visible(Response response) {
        return response.getBody().asString().replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private void registerUser(String username, String email, String password) {
        CsrfClient.form("/register")
            .contentType(ContentType.URLENC)
            .formParam("username", username)
            .formParam("email", email)
            .formParam("password", password)
            .formParam("confirmPassword", password)
        .when()
            .post("/register");
    }

    private String loginAndGetToken(String username, String email, String password) {
        registerUser(username, email, password);
        return CsrfClient.form("/login")
            .contentType(ContentType.URLENC)
            .formParam("username", username)
            .formParam("password", password)
        .when()
            .post("/login")
        .then()
            .extract().cookie("jwt");
    }
}
