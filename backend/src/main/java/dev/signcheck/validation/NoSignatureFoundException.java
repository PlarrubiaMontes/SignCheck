package dev.signcheck.validation;

import dev.signcheck.api.error.ErrorCode;
import dev.signcheck.api.error.SignCheckException;

/** The file has a supported format (for example a PDF) but contains no signature. */
public class NoSignatureFoundException extends SignCheckException {

    public NoSignatureFoundException() {
        super(ErrorCode.NO_SIGNATURE_FOUND, "The file contains no signature");
    }
}
