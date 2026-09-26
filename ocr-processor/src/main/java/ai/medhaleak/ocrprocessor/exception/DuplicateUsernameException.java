package ai.medhaleak.ocrprocessor.exception;

public class DuplicateUsernameException extends RuntimeException {
    public DuplicateUsernameException(String username) {
        super("Username already taken: " + username);
    }
}
