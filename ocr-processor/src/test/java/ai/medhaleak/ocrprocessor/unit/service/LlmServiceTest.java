package ai.medhaleak.ocrprocessor.unit.service;

import ai.medhaleak.ocrprocessor.config.AppProperties;
import ai.medhaleak.ocrprocessor.exception.LlmUnavailableException;
import ai.medhaleak.ocrprocessor.service.SpringAiLlmService;
import ai.medhaleak.ocrprocessor.testing.Evidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmServiceTest {

    @Mock ChatClient chatClient;
    @Mock ChatClient.ChatClientRequestSpec requestSpec;
    @Mock ChatClient.CallResponseSpec callSpec;

    private SpringAiLlmService llmService;
    private AppProperties props;

    @BeforeEach
    void setUp() {
        props = new AppProperties(
                new AppProperties.Jwt("secret", 8),
                new AppProperties.Ocr(20_971_520L, List.of()),
                new AppProperties.Llm(20, "Summarise: {extractedText}"),
                new AppProperties.Security(5, 15)
        );
        llmService = new SpringAiLlmService(chatClient, props);
    }

    @Test
    void summarise_success_returnsContent() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("Great summary");

        String result = llmService.summarise("Some text");

        assertThat(result).isEqualTo("Great summary");
        Evidence.record("extractedText=\"Some text\"", "model call returned content", "summary=\"" + result + "\"");
    }

    @Test
    void summarise_truncatesLongInput() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("ok");

        // maxInputChars = 20 in test props
        String longText = "A".repeat(100);
        llmService.summarise(longText);

        // Verify prompt was called with truncated text (≤ 20 chars in input)
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(prompt.capture());
        assertThat(prompt.getValue()).contains("A".repeat(20)).doesNotContain("A".repeat(21));
        Evidence.record("extractedText length=100 maxInputChars=20", "prompt truncated before the model call",
                "prompt=\"" + prompt.getValue() + "\"");
    }

    @Test
    void summarise_emptyText_returnsEmpty() {
        assertThat(llmService.summarise("")).isEqualTo("");
        assertThat(llmService.summarise(null)).isEqualTo("");
        verifyNoInteractions(chatClient);
        Evidence.record("extractedText=\"\" and null", "model not called", "summary=\"\"");
    }

    @Test
    void summarise_llmThrows_wrapsException() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenThrow(new RuntimeException("API down"));

        Throwable error = catchThrowable(() -> llmService.summarise("text"));
        assertThat(error).isInstanceOf(LlmUnavailableException.class)
                .hasMessageContaining("LLM service unavailable");
        Evidence.record("extractedText=\"text\" model throws RuntimeException \"API down\"",
                error.getClass().getSimpleName(), error.getMessage());
    }
}
