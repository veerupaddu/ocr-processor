package ai.medhaleak.ocrprocessor.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import ai.medhaleak.ocrprocessor.service.LlmService;
import ai.medhaleak.ocrprocessor.service.OcrEngine;
import ai.medhaleak.ocrprocessor.repository.UserRepository;
import ai.medhaleak.ocrprocessor.testing.Evidence;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ApplicationContextIT {

    @MockBean LlmService llmService;
    @MockBean OcrEngine ocrEngine;

    @Autowired UserRepository userRepository;

    @Test
    void contextLoads() {
        assertThat(userRepository).isNotNull();
        Evidence.record("profile=test datasource=jdbc:tc:postgresql:16",
                "context started", "UserRepository=" + userRepository.getClass().getSimpleName());
    }
}
