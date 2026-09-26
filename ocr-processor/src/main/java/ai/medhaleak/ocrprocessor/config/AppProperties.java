package ai.medhaleak.ocrprocessor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Ocr ocr,
        Llm llm,
        Security security
) {
    public record Jwt(String secret, int expiryHours) {}
    public record Ocr(long maxFileSizeBytes, List<String> allowedTypes) {}
    public record Llm(int maxInputChars, String promptTemplate) {}
    public record Security(int maxLoginAttempts, int lockoutDurationMinutes) {}
}
