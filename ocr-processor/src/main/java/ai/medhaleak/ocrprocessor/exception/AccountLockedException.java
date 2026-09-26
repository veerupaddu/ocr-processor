package ai.medhaleak.ocrprocessor.exception;

public class AccountLockedException extends RuntimeException {
    public AccountLockedException() {
        super("Account temporarily locked. Try again later.");
    }
}
