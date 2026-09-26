package ai.medhaleak.ocrprocessor.integration;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;

/** Loads the form, keeps its session, and sends the CSRF token with the post. */
final class CsrfClient {

    private static final Pattern TOKEN = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    private CsrfClient() {
    }

    static RequestSpecification form(String path) {
        return form(path, Map.of());
    }

    static RequestSpecification form(String path, Map<String, String> cookies) {
        Response page = given().cookies(cookies).redirects().follow(false).get(path);
        Matcher matcher = TOKEN.matcher(page.asString());
        if (!matcher.find()) {
            throw new IllegalStateException("CSRF token missing on " + path);
        }
        return given()
                .cookies(page.getCookies())
                .cookies(cookies)
                .formParam("_csrf", matcher.group(1));
    }

    static void register(String username, String email, String password) {
        form("/register")
                .contentType(ContentType.URLENC)
                .formParam("username", username)
                .formParam("email", email)
                .formParam("password", password)
                .formParam("confirmPassword", password)
                .when()
                .post("/register");
    }

    static String login(String username, String password) {
        return form("/login")
                .contentType(ContentType.URLENC)
                .formParam("username", username)
                .formParam("password", password)
                .when()
                .post("/login")
                .then()
                .extract().cookie("jwt");
    }
}
