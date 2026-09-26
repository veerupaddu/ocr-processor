package ai.medhaleak.ocrprocessor.unit.service;

import ai.medhaleak.ocrprocessor.config.AppProperties;
import ai.medhaleak.ocrprocessor.config.JwtUtil;
import ai.medhaleak.ocrprocessor.testing.Evidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.jsonwebtoken.JwtException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                new AppProperties.Jwt("test-secret-key-that-is-long-enough-32chars!", 8),
                new AppProperties.Ocr(0L, List.of()),
                new AppProperties.Llm(0, ""),
                new AppProperties.Security(5, 15)
        );
        jwtUtil = new JwtUtil(props);
    }

    @Test
    void generateAndParse_roundtrip() {
        UUID userId = UUID.randomUUID();
        String token = jwtUtil.generateToken(userId, "alice");

        UUID parsed = jwtUtil.extractUserId(token);
        assertThat(token).isNotBlank();
        assertThat(parsed).isEqualTo(userId);
        Evidence.record("userId=" + userId + " username=alice", "token issued and parsed",
                "userId=" + parsed + " token=" + token.substring(0, Math.min(24, token.length())) + "…");
    }

    @Test
    void parseToken_invalidToken_throwsJwtException() {
        Throwable error = catchThrowable(() -> jwtUtil.parseToken("not.a.valid.token"));
        assertThat(error).isInstanceOf(JwtException.class);
        Evidence.record("token=\"not.a.valid.token\"", error.getClass().getSimpleName(), error.getMessage());
    }

    @Test
    void parseToken_tamperedToken_throwsJwtException() {
        UUID userId = UUID.randomUUID();
        String token = jwtUtil.generateToken(userId, "alice");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        Throwable error = catchThrowable(() -> jwtUtil.parseToken(tampered));
        assertThat(error).isInstanceOf(JwtException.class);
        Evidence.record("signature replaced with XXXXX", error.getClass().getSimpleName(), error.getMessage());
    }

    @Test
    void claims_containUsername() {
        UUID userId = UUID.randomUUID();
        String token = jwtUtil.generateToken(userId, "bob");

        Object username = jwtUtil.extractUsername(token);
        assertThat(username).isEqualTo("bob");
        Evidence.record("userId=" + userId + " username=bob", "claims read", "username=" + username);
    }
}
