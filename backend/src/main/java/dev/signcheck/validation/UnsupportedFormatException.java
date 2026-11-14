package dev.signcheck.validation;

import dev.signcheck.api.error.ErrorCode;
import dev.signcheck.api.error.SignCheckException;

/** The uploaded file is not a signed document format DSS can handle (PDF, XML signature, CMS...). */
public class UnsupportedFormatException extends SignCheckException {

    public UnsupportedFormatException(Throwable cause) {
        super(ErrorCode.UNSUPPORTED_FORMAT, "The file is not a supported signed document", cause);
    }
}
