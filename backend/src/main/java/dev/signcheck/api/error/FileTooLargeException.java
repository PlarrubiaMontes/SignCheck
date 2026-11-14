package dev.signcheck.api.error;

import org.springframework.util.unit.DataSize;

/** An uploaded file exceeds {@code signcheck.upload.max-file-size}. */
public class FileTooLargeException extends SignCheckException {

    public FileTooLargeException(DataSize limit) {
        super(ErrorCode.FILE_TOO_LARGE, "The file exceeds the maximum allowed size of " + limit.toKilobytes() + " KB");
    }
}
