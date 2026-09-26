package ai.medhaleak.ocrprocessor.dto;

import java.time.Instant;
import java.util.UUID;

public record OcrRecordDto(
        UUID id,
        String documentName,
        String originalFilename,
        String fileType,
        long fileSizeBytes,
        String status,
        String summaryStatus,
        String extractedText,
        String summary,
        Instant createdAt,
        Instant updatedAt
) {}
