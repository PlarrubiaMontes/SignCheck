package dev.signcheck.api.error;

/**
 * Base class of the expected, client-facing failures. Each one carries the {@link ErrorCode} that
 * {@link GlobalExceptionHandler} turns into the HTTP status and {@code code} of the response.
 * Its message is safe to show to clients (no file content, no internal details).
 */
public abstract class SignCheckException extends RuntimeException {

    private final ErrorCode errorCode;

    protected SignCheckException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected SignCheckException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
