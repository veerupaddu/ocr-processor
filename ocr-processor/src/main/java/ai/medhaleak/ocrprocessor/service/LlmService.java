package ai.medhaleak.ocrprocessor.service;

import ai.medhaleak.ocrprocessor.exception.LlmUnavailableException;

public interface LlmService {
    String summarise(String text) throws LlmUnavailableException;
}
