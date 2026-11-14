package dev.signcheck.signing;

import dev.signcheck.api.error.FileTooLargeException;
import dev.signcheck.api.error.NoFileException;
import dev.signcheck.config.SignCheckProperties;
import dev.signcheck.signing.SigningOptions.Format;
import dev.signcheck.signing.SigningOptions.Level;
import dev.signcheck.signing.SigningOptions.Packaging;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * {@code /api/v1/signatures}: sign a document with the TEST-ONLY demo certificate. Exists only when
 * {@code signcheck.signing.enabled=true}; otherwise the path answers 404.
 */
@RestController
@RequestMapping("/api/v1/signatures")
@ConditionalOnProperty(prefix = "signcheck.signing", name = "enabled", havingValue = "true")
@Tag(name = "Signing (demo)", description = "Sign documents with a TEST-ONLY demo certificate")
public class SigningController {

    private final SigningService signingService;
    private final DemoCertificateAuthority demoCa;
    private final DataSize maxFileSize;

    public SigningController(SigningService signingService, DemoCertificateAuthority demoCa,
            SignCheckProperties properties) {
        this.signingService = signingService;
        this.demoCa = demoCa;
        this.maxFileSize = properties.upload().maxFileSize();
    }

    /**
     * @param formats available signature formats
     * @param levels  available levels ({@code T} only when a time-stamping authority is configured)
     * @param signer  subject of the demo signing certificate
     */
    public record SigningCapabilities(List<Format> formats, List<Level> levels, String signer) {
    }

    @Operation(summary = "Signing options", description = "Formats and levels this server can produce.")
    @GetMapping(path = "/options", produces = MediaType.APPLICATION_JSON_VALUE)
    public SigningCapabilities options() {
        return new SigningCapabilities(List.of(Format.values()),
                signingService.timestampingAvailable() ? List.of(Level.B, Level.T) : List.of(Level.B),
                demoCa.signer().getSubjectX500Principal().getName());
    }

    @Operation(summary = "Sign a document with the demo certificate",
            description = """
                    Returns the signed document (or the detached signature) as a download. The certificate is a \
                    TEST-ONLY demo certificate: the result validates as VALID only on a SignCheck that trusts the \
                    same demo CA. The file is processed in memory and never stored.""")
    @ApiResponse(responseCode = "200", description = "The signed file")
    @ApiResponse(responseCode = "400", description = "Missing file or unsupported combination "
            + "(code NO_FILE or INVALID_SIGNING_REQUEST)")
    // No 'produces': the content type depends on the format, and errors must still render as problem+json
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> sign(
            @Parameter(description = "Document to sign") @RequestPart("file") MultipartFile file,
            @Parameter(description = "PADES (PDF only), XADES or CADES") @RequestParam("format") Format format,
            @Parameter(description = "ENVELOPED, ENVELOPING or DETACHED; default depends on the format")
            @RequestParam(name = "packaging", required = false) Packaging packaging,
            @Parameter(description = "B or T (T needs a configured time-stamping authority)")
            @RequestParam(name = "level", defaultValue = "B") Level level) throws IOException {
        if (file.isEmpty()) {
            throw new NoFileException();
        }
        if (file.getSize() > maxFileSize.toBytes()) {
            throw new FileTooLargeException(maxFileSize);
        }
        SigningService.SignedFile signed =
                signingService.sign(file.getOriginalFilename(), file.getBytes(), format, packaging, level);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(signed.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(signed.name()).build().toString())
                .body(signed.content());
    }
}
