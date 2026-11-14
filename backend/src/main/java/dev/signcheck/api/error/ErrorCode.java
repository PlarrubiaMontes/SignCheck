package dev.signcheck.api.error;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes returned in the {@code code} property of every error response
 * (RFC 9457 {@code ProblemDetail}). Clients must rely on the code, never on the human-readable texts.
 */
public enum ErrorCode {

    NO_FILE(HttpStatus.BAD_REQUEST, "No file"),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "File too large"),
    NO_SIGNATURE_FOUND(HttpStatus.UNPROCESSABLE_CONTENT, "No signature found"),
    UNSUPPORTED_FORMAT(HttpStatus.UNPROCESSABLE_CONTENT, "Unsupported format"),
    INVALID_SIGNING_REQUEST(HttpStatus.BAD_REQUEST, "Invalid signing request"),
    VALIDATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Validation error");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }
}
