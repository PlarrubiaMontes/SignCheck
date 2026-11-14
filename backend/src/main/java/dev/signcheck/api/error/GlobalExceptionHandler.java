package dev.signcheck.api.error;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into an RFC 9457 {@link ProblemDetail} with an extra, stable {@code code}
 * property (see {@link ErrorCode}).
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} keeps Spring MVC's own ProblemDetail responses for
 * standard errors (405, 415...); the two upload-related ones are mapped to our codes and the others get the
 * HTTP status name as {@code code} (e.g. {@code UNSUPPORTED_MEDIA_TYPE}), so every error carries one.
 * Unexpected exceptions become a generic {@code VALIDATION_ERROR}: the stack trace is logged, never returned.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /** Name of the extra ProblemDetail property holding the {@link ErrorCode}. */
    public static final String CODE_PROPERTY = "code";

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(SignCheckException.class)
    ProblemDetail handleSignCheckException(SignCheckException exception) {
        return problem(exception.errorCode(), exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        LOG.error("Unexpected error while processing the request", exception);
        return problem(ErrorCode.VALIDATION_ERROR, "The document could not be validated due to an internal error");
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestPart(MissingServletRequestPartException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return response(problem(ErrorCode.NO_FILE, "The '" + exception.getRequestPartName() + "' part is required"));
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return response(problem(ErrorCode.FILE_TOO_LARGE, "The upload exceeds the maximum allowed size"));
    }

    /** Adds a {@code code} to the ProblemDetail responses Spring MVC builds for its standard exceptions. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(exception, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey(CODE_PROPERTY))) {
            HttpStatus status = HttpStatus.resolve(statusCode.value());
            problem.setProperty(CODE_PROPERTY, status == null ? "HTTP_" + statusCode.value() : status.name());
        }
        return response;
    }

    static ProblemDetail problem(ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setTitle(code.title());
        problem.setType(URI.create("about:blank"));
        problem.setProperty(CODE_PROPERTY, code.name());
        return problem;
    }

    private static ResponseEntity<Object> response(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }
}
