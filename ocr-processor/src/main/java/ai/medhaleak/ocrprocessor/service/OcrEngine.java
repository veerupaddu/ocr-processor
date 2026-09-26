package ai.medhaleak.ocrprocessor.service;

import ai.medhaleak.ocrprocessor.exception.OcrExtractionException;

public interface OcrEngine {
    String extract(byte[] fileBytes, String mimeType) throws OcrExtractionException;
}
