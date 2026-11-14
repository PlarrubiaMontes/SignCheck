package dev.signcheck.validation;

import dev.signcheck.config.SignCheckProperties;
import dev.signcheck.validation.dto.ValidationResponse;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.spi.validation.CertificateVerifier;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.validation.reports.Reports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Validates every signature in an uploaded document with the default ETSI validation policy.
 *
 * <h2>What "validating a signature" means</h2>
 * Following ETSI EN 319 102-1, DSS checks for each signature that:
 * <ol>
 *   <li>the signed data was not modified (the hash in the signature matches the document);</li>
 *   <li>the signature value is mathematically correct for the signer's public key;</li>
 *   <li>the signer's certificate chains up to a trusted anchor and every certificate was valid,
 *       allowed to sign and not revoked at the relevant time;</li>
 *   <li>the algorithms used are still considered secure.</li>
 * </ol>
 *
 * <h2>Detached signatures</h2>
 * A detached signature only contains the <em>hash</em> of the signed file. To check it, the validator
 * needs the original file(s): they are passed to DSS as "detached contents". Without them DSS cannot
 * recompute the hash and the verdict is {@code INDETERMINATE} / {@code SIGNED_DATA_NOT_FOUND}.
 *
 * <p>The {@link CertificateVerifier} bean decides where trust anchors and revocation data come from
 * (see {@code TrustMode}); this service never opens network connections itself.
 *
 * <p>The document is processed in memory and discarded; only metadata is logged.
 */
@Service
public class ValidationService {

    private static final Logger LOG = LoggerFactory.getLogger(ValidationService.class);

    private final DocumentValidatorFactory validatorFactory;
    private final CertificateVerifier certificateVerifier;
    private final ValidationReportMapper mapper;
    private final SignCheckProperties properties;

    public ValidationService(DocumentValidatorFactory validatorFactory, CertificateVerifier certificateVerifier,
            ValidationReportMapper mapper, SignCheckProperties properties) {
        this.validatorFactory = validatorFactory;
        this.certificateVerifier = certificateVerifier;
        this.mapper = mapper;
        this.properties = properties;
    }

    /**
     * @throws UnsupportedFormatException if the file is not a supported signed document
     * @throws NoSignatureFoundException  if the file is supported but has no signature
     */
    public ValidationResponse validate(ValidationRequest request) {
        DSSDocument document = toDocument(request.signedFile());
        SignedDocumentValidator validator = validatorFactory.create(document);
        validator.setCertificateVerifier(certificateVerifier);
        // The ETSI TS 119 102-2 report is costly to build: only when the client asked for raw reports
        validator.setEnableEtsiValidationReport(request.includeRawReports());
        if (!request.originals().isEmpty()) {
            validator.setDetachedContents(request.originals().stream().map(ValidationService::toDocument).toList());
        }

        Reports reports = validator.validateDocument();
        if (reports.getSimpleReport().getSignaturesCount() == 0) {
            throw new NoSignatureFoundException();
        }

        ValidationResponse response = mapper.toResponse(request.signedFile().name(), reports,
                properties.trust().mode(), request.includeRawReports(), request.locale());
        LOG.info("Validated document: size={} bytes, originals={}, format={}, signatures={}, verdict={}",
                request.signedFile().content().length, request.originals().size(), response.detectedFormat(),
                response.summary().signatureCount(), response.summary().overallVerdict());
        return response;
    }

    private static DSSDocument toDocument(ValidationRequest.UploadedFile file) {
        return new InMemoryDocument(file.content(), file.name());
    }
}
