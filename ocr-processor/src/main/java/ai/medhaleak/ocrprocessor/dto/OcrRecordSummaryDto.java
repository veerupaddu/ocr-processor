package ai.medhaleak.ocrprocessor.dto;

import java.time.Instant;
import java.util.UUID;

public record OcrRecordSummaryDto(
        UUID id,
        String documentName,
        String status,
        String preview,
        Instant createdAt
) {}
