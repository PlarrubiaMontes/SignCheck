package dev.signcheck.validation;

import dev.signcheck.api.error.FileTooLargeException;
import dev.signcheck.api.error.NoFileException;
import dev.signcheck.api.error.ProblemExamples;
import dev.signcheck.config.SignCheckProperties;
import dev.signcheck.validation.dto.ValidationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * {@code POST /api/v1/validations}: upload a signed file (plus, for detached signatures, the original
 * documents) and get a verdict per signature.
 */
@RestController
@RequestMapping("/api/v1/validations")
@Tag(name = "Validation", description = "Validate the electronic signatures of a document")
public class ValidationController {

    private static final String PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private final ValidationService validationService;
    private final DataSize maxFileSize;

    public ValidationController(ValidationService validationService, SignCheckProperties properties) {
        this.validationService = validationService;
        this.maxFileSize = properties.upload().maxFileSize();
    }

    @Operation(
            summary = "Validate the signatures of a document",
            description = """
                    Detects the format from the content (PAdES, XAdES, CAdES), validates every signature with the \
                    default ETSI validation policy and the configured trust anchors, and returns a verdict \
                    (VALID / INDETERMINATE / INVALID) per signature plus a summary. Send `Accept-Language: es` for \
                    Spanish reasons. The file is processed in memory and never stored.""")
    @ApiResponse(responseCode = "200", description = "Validation performed (whatever the verdict)")
    @ApiResponse(responseCode = "400", description = "The `file` part is missing or empty",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "NO_FILE", value = ProblemExamples.NO_FILE)))
    @ApiResponse(responseCode = "413", description = "A file exceeds `signcheck.upload.max-file-size`",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "FILE_TOO_LARGE", value = ProblemExamples.FILE_TOO_LARGE)))
    @ApiResponse(responseCode = "422", description = "The file is not a signed document SignCheck can validate",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class),
                    examples = {
                            @ExampleObject(name = "UNSUPPORTED_FORMAT", value = ProblemExamples.UNSUPPORTED_FORMAT),
                            @ExampleObject(name = "NO_SIGNATURE_FOUND", value = ProblemExamples.NO_SIGNATURE_FOUND)}))
    @ApiResponse(responseCode = "500", description = "Unexpected failure (no internal details are returned)",
            content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class),
                    examples = @ExampleObject(name = "VALIDATION_ERROR", value = ProblemExamples.VALIDATION_ERROR)))
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ValidationResponse validate(
            @Parameter(description = "The signed file: PDF, XML signature, .p7m/.p7s...")
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "Original document(s) signed by a detached signature")
            @RequestPart(name = "originals", required = false) List<MultipartFile> originals,
            @Parameter(description = "Also return the DSS XML reports (simple, detailed, diagnostic data, ETSI)")
            @RequestParam(name = "includeRawReports", defaultValue = "false") boolean includeRawReports,
            @Parameter(hidden = true) Locale locale) throws IOException {
        if (file.isEmpty()) {
            throw new NoFileException();
        }
        List<ValidationRequest.UploadedFile> originalFiles = new ArrayList<>();
        for (MultipartFile original : originals == null ? List.<MultipartFile>of() : originals) {
            if (!original.isEmpty()) {
                originalFiles.add(toUploadedFile(original));
            }
        }
        return validationService.validate(
                new ValidationRequest(toUploadedFile(file), originalFiles, includeRawReports, locale));
    }

    private ValidationRequest.UploadedFile toUploadedFile(MultipartFile file) throws IOException {
        // The servlet container enforces the same limit (spring.servlet.multipart); this check keeps the
        // rule explicit and independent of the container.
        if (file.getSize() > maxFileSize.toBytes()) {
            throw new FileTooLargeException(maxFileSize);
        }
        return new ValidationRequest.UploadedFile(file.getOriginalFilename(), file.getBytes());
    }
}
