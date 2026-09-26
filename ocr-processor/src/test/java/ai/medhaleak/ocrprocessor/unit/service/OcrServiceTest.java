package ai.medhaleak.ocrprocessor.unit.service;

import ai.medhaleak.ocrprocessor.config.AppProperties;
import ai.medhaleak.ocrprocessor.dto.OcrRecordDto;
import ai.medhaleak.ocrprocessor.exception.LlmUnavailableException;
import ai.medhaleak.ocrprocessor.exception.OcrExtractionException;
import ai.medhaleak.ocrprocessor.exception.ResourceNotFoundException;
import ai.medhaleak.ocrprocessor.model.OcrRecord;
import ai.medhaleak.ocrprocessor.model.User;
import ai.medhaleak.ocrprocessor.repository.OcrRecordRepository;
import ai.medhaleak.ocrprocessor.repository.UserRepository;
import ai.medhaleak.ocrprocessor.service.LlmService;
import ai.medhaleak.ocrprocessor.service.OcrEngine;
import ai.medhaleak.ocrprocessor.service.OcrService;
import ai.medhaleak.ocrprocessor.testing.Evidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OcrServiceTest {

    @Mock OcrRecordRepository ocrRecordRepository;
    @Mock UserRepository userRepository;
    @Mock OcrEngine ocrEngine;
    @Mock LlmService llmService;

    private OcrService ocrService;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties(
                new AppProperties.Jwt("secret", 8),
                new AppProperties.Ocr(20_971_520L, List.of("application/pdf", "image/png", "image/jpeg", "image/tiff")),
                new AppProperties.Llm(10000, "Summarise: {extractedText}"),
                new AppProperties.Security(5, 15)
        );
        ocrService = new OcrService(ocrRecordRepository, userRepository, ocrEngine, llmService, props);
    }

    @Test
    void processUpload_success_extractsAndSummarises() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = new User();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(ocrRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ocrEngine.extract(any(), eq("image/png"))).thenReturn("Extracted text");
        when(llmService.summarise("Extracted text")).thenReturn("A nice summary");

        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", new byte[100]);
        OcrRecordDto dto = ocrService.processUpload(userId, file, "My Doc");

        assertThat(dto.status()).isEqualTo("COMPLETE");
        assertThat(dto.extractedText()).isEqualTo("Extracted text");
        assertThat(dto.summary()).isEqualTo("A nice summary");
        assertThat(dto.summaryStatus()).isEqualTo("COMPLETE");
        Evidence.record("file=test.png type=image/png documentName=\"My Doc\" engine text=\"Extracted text\"",
                dto.status() + " / summary " + dto.summaryStatus(),
                "extractedText=\"" + dto.extractedText() + "\" summary=\"" + dto.summary() + "\"");
    }

    @Test
    void processUpload_ocrFails_setsStatusFailed() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = new User();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(ocrRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ocrEngine.extract(any(), anyString())).thenThrow(new OcrExtractionException("fail", new RuntimeException()));

        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", new byte[100]);
        OcrRecordDto dto = ocrService.processUpload(userId, file, null);

        assertThat(dto.status()).isEqualTo("FAILED");
        verify(llmService, never()).summarise(anyString());
        Evidence.record("file=test.png engine throws OcrExtractionException \"fail\"",
                dto.status(), "summary was not called");
    }

    @Test
    void processUpload_llmFails_setsStatusFailed() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = new User();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(ocrRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ocrEngine.extract(any(), anyString())).thenReturn("some text");
        when(llmService.summarise(anyString())).thenThrow(new LlmUnavailableException("down", new RuntimeException()));

        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", new byte[100]);
        OcrRecordDto dto = ocrService.processUpload(userId, file, null);

        assertThat(dto.status()).isEqualTo("COMPLETE");
        assertThat(dto.extractedText()).isEqualTo("some text");
        assertThat(dto.summaryStatus()).isEqualTo("FAILED");
        Evidence.record("file=test.png extractedText=\"some text\" model throws LlmUnavailableException \"down\"",
                dto.status() + " / summary " + dto.summaryStatus(),
                "extractedText=\"" + dto.extractedText() + "\" summary=" + dto.summary());
    }

    @Test
    void processUpload_emptyText_skipsSummarisation() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = new User();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(ocrRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(ocrEngine.extract(any(), anyString())).thenReturn("");

        MockMultipartFile file = new MockMultipartFile("file", "blank.png", "image/png", new byte[100]);
        OcrRecordDto dto = ocrService.processUpload(userId, file, null);

        assertThat(dto.summaryStatus()).isEqualTo("SKIPPED");
        verify(llmService, never()).summarise(anyString());
        Evidence.record("file=blank.png engine returns \"\"",
                dto.status() + " / summary " + dto.summaryStatus(), "model was not called");
    }

    @Test
    void processUpload_unsupportedType_throws() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        MockMultipartFile file = new MockMultipartFile("file", "doc.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[100]);

        Throwable error = catchThrowable(() -> ocrService.processUpload(userId, file, null));
        assertThat(error).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported file type");
        Evidence.record("file=doc.docx type=application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                error.getClass().getSimpleName(), error.getMessage());
    }

    @Test
    void getById_notFound_throws() {
        UUID userId = UUID.randomUUID();
        UUID recId = UUID.randomUUID();
        when(ocrRecordRepository.findByIdAndUserId(recId, userId)).thenReturn(Optional.empty());

        Throwable error = catchThrowable(() -> ocrService.getById(userId, recId));
        assertThat(error).isInstanceOf(ResourceNotFoundException.class);
        Evidence.record("userId=" + userId + " recordId=" + recId, error.getClass().getSimpleName(), error.getMessage());
    }

    @Test
    void retrySummary_onlyAllowedWhenFailed() {
        UUID userId = UUID.randomUUID();
        UUID recId = UUID.randomUUID();
        OcrRecord record = savedRecord(userId, OcrRecord.Status.COMPLETE, OcrRecord.SummaryStatus.COMPLETE);
        when(ocrRecordRepository.findByIdAndUserId(recId, userId)).thenReturn(Optional.of(record));

        Throwable refused = catchThrowable(() -> ocrService.retrySummary(userId, recId));
        assertThat(refused).isInstanceOf(IllegalStateException.class).hasMessageContaining("FAILED");

        record.setSummaryStatus(OcrRecord.SummaryStatus.FAILED);
        record.setExtractedText("body");
        when(llmService.summarise("body")).thenReturn("retried summary");
        when(ocrRecordRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        OcrRecordDto dto = ocrService.retrySummary(userId, recId);

        assertThat(dto.summary()).isEqualTo("retried summary");
        assertThat(dto.summaryStatus()).isEqualTo("COMPLETE");
        Evidence.record("record summaryStatus=COMPLETE then FAILED extractedText=\"body\"",
                refused.getClass().getSimpleName() + " then " + dto.summaryStatus(),
                "refused: " + refused.getMessage() + " · retried summary=\"" + dto.summary() + "\"");
    }

    private OcrRecord savedRecord(UUID userId, OcrRecord.Status status, OcrRecord.SummaryStatus sumStatus) {
        OcrRecord r = new OcrRecord();
        User u = new User(); r.setUser(u);
        r.setDocumentName("test.png");
        r.setOriginalFilename("test.png");
        r.setFileType("image/png");
        r.setFileSizeBytes(100);
        r.setStatus(status);
        r.setSummaryStatus(sumStatus);
        return r;
    }
}
