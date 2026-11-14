package dev.signcheck.signing;

import dev.signcheck.api.error.ErrorCode;
import dev.signcheck.api.error.SignCheckException;

/** The requested combination of format, packaging, level and file cannot be signed. */
public class InvalidSigningRequestException extends SignCheckException {

    public InvalidSigningRequestException(String message) {
        super(ErrorCode.INVALID_SIGNING_REQUEST, message);
    }

    public InvalidSigningRequestException(String message, Throwable cause) {
        super(ErrorCode.INVALID_SIGNING_REQUEST, message, cause);
    }
}
