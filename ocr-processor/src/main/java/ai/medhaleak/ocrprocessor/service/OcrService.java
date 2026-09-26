package ai.medhaleak.ocrprocessor.service;

import ai.medhaleak.ocrprocessor.config.AppProperties;
import ai.medhaleak.ocrprocessor.dto.OcrRecordDto;
import ai.medhaleak.ocrprocessor.dto.OcrRecordSummaryDto;
import ai.medhaleak.ocrprocessor.exception.LlmUnavailableException;
import ai.medhaleak.ocrprocessor.exception.OcrExtractionException;
import ai.medhaleak.ocrprocessor.exception.ResourceNotFoundException;
import ai.medhaleak.ocrprocessor.model.OcrRecord;
import ai.medhaleak.ocrprocessor.model.User;
import ai.medhaleak.ocrprocessor.repository.OcrRecordRepository;
import ai.medhaleak.ocrprocessor.repository.UserRepository;
import org.apache.tika.Tika;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Service
@Transactional
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);
    private static final int PREVIEW_MAX_CHARS = 200;

    private final OcrRecordRepository ocrRecordRepository;
    private final UserRepository userRepository;
    private final OcrEngine ocrEngine;
    private final LlmService llmService;
    private final AppProperties props;
    private final Tika tika = new Tika();

    public OcrService(OcrRecordRepository ocrRecordRepository, UserRepository userRepository,
                      OcrEngine ocrEngine, LlmService llmService, AppProperties props) {
        this.ocrRecordRepository = ocrRecordRepository;
        this.userRepository = userRepository;
        this.ocrEngine = ocrEngine;
        this.llmService = llmService;
        this.props = props;
    }

    public OcrRecordDto processUpload(UUID userId, MultipartFile file, String documentName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read uploaded file", e);
        }

        String mimeType = tika.detect(bytes, file.getOriginalFilename());
        validateFile(mimeType, bytes.length);

        String resolvedName = (documentName == null || documentName.isBlank())
                ? file.getOriginalFilename() : documentName;

        OcrRecord record = new OcrRecord();
        record.setUser(user);
        record.setDocumentName(resolvedName);
        record.setOriginalFilename(file.getOriginalFilename());
        record.setFileType(mimeType);
        record.setFileSizeBytes(bytes.length);
        record.setStatus(OcrRecord.Status.PROCESSING);
        record = ocrRecordRepository.save(record);

        String extractedText = "";
        try {
            extractedText = ocrEngine.extract(bytes, mimeType);
            record.setExtractedText(extractedText);
            record.setStatus(OcrRecord.Status.COMPLETE);
        } catch (OcrExtractionException e) {
            log.error("OCR failed for record {}", record.getId(), e);
            record.setStatus(OcrRecord.Status.FAILED);
            record = ocrRecordRepository.save(record);
            return toDto(record);
        }

        if (extractedText.isBlank()) {
            record.setSummaryStatus(OcrRecord.SummaryStatus.SKIPPED);
        } else {
            try {
                record.setSummary(llmService.summarise(extractedText));
                record.setSummaryStatus(OcrRecord.SummaryStatus.COMPLETE);
            } catch (LlmUnavailableException e) {
                log.warn("LLM summarisation failed for record {}", record.getId(), e);
                record.setSummaryStatus(OcrRecord.SummaryStatus.FAILED);
            }
        }

        return toDto(ocrRecordRepository.save(record));
    }

    @Transactional(readOnly = true)
    public Page<OcrRecordSummaryDto> search(UUID userId, String query, Pageable pageable) {
        String q = (query == null) ? "" : query.trim();
        return ocrRecordRepository.searchByUser(userId, q, pageable).map(this::toSummaryDto);
    }

    @Transactional(readOnly = true)
    public OcrRecordDto getById(UUID userId, UUID recordId) {
        return toDto(ocrRecordRepository.findByIdAndUserId(recordId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Record not found")));
    }

    public OcrRecordDto retrySummary(UUID userId, UUID recordId) {
        OcrRecord record = ocrRecordRepository.findByIdAndUserId(recordId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Record not found"));

        if (record.getSummaryStatus() != OcrRecord.SummaryStatus.FAILED) {
            throw new IllegalStateException("Summary retry only allowed when summaryStatus is FAILED");
        }

        try {
            record.setSummary(llmService.summarise(record.getExtractedText()));
            record.setSummaryStatus(OcrRecord.SummaryStatus.COMPLETE);
        } catch (LlmUnavailableException e) {
            log.warn("LLM retry failed for record {}", record.getId(), e);
            record.setSummaryStatus(OcrRecord.SummaryStatus.FAILED);
        }

        return toDto(ocrRecordRepository.save(record));
    }

    private void validateFile(String mimeType, long sizeBytes) {
        if (!props.ocr().allowedTypes().contains(mimeType)) {
            throw new IllegalArgumentException("Unsupported file type. Allowed: PDF, PNG, JPG, JPEG, TIFF.");
        }
        if (sizeBytes > props.ocr().maxFileSizeBytes()) {
            throw new IllegalArgumentException("File too large. Maximum size is 20 MB.");
        }
    }

    private OcrRecordDto toDto(OcrRecord r) {
        return new OcrRecordDto(r.getId(), r.getDocumentName(), r.getOriginalFilename(),
                r.getFileType(), r.getFileSizeBytes(), r.getStatus().name(),
                r.getSummaryStatus().name(), r.getExtractedText(), r.getSummary(),
                r.getCreatedAt(), r.getUpdatedAt());
    }

    private OcrRecordSummaryDto toSummaryDto(OcrRecord r) {
        String text = r.getExtractedText();
        String preview = (text != null && text.length() > PREVIEW_MAX_CHARS)
                ? text.substring(0, PREVIEW_MAX_CHARS) + "…" : (text != null ? text : "");
        return new OcrRecordSummaryDto(r.getId(), r.getDocumentName(),
                r.getStatus().name(), preview, r.getCreatedAt());
    }
}
