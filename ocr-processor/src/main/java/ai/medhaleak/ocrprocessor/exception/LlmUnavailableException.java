package ai.medhaleak.ocrprocessor.exception;

public class LlmUnavailableException extends RuntimeException {
    public LlmUnavailableException(String message, Throwable cause) { super(message, cause); }
}
