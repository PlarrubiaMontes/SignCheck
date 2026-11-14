package dev.signcheck.api.error;

/** The {@code file} part was sent but is empty. */
public class NoFileException extends SignCheckException {

    public NoFileException() {
        super(ErrorCode.NO_FILE, "The 'file' part is required and must not be empty");
    }
}
