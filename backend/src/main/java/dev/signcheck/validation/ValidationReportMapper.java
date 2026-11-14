package dev.signcheck.validation;

import dev.signcheck.trust.TrustMode;
import dev.signcheck.validation.dto.CertificateInfo;
import dev.signcheck.validation.dto.RawReports;
import dev.signcheck.validation.dto.RevocationInfo;
import dev.signcheck.validation.dto.SignatureResult;
import dev.signcheck.validation.dto.TimestampInfo;
import dev.signcheck.validation.dto.SignerInfo;
import dev.signcheck.validation.dto.ValidationResponse;
import dev.signcheck.validation.dto.ValidationSummary;
import dev.signcheck.validation.dto.Verdict;
import eu.europa.esig.dss.diagnostic.CertificateRevocationWrapper;
import eu.europa.esig.dss.diagnostic.CertificateWrapper;
import eu.europa.esig.dss.diagnostic.DiagnosticData;
import eu.europa.esig.dss.diagnostic.SignatureWrapper;
import eu.europa.esig.dss.diagnostic.TimestampWrapper;
import eu.europa.esig.dss.enumerations.Indication;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignatureQualification;
import eu.europa.esig.dss.enumerations.SubIndication;
import eu.europa.esig.dss.jaxb.object.Message;
import eu.europa.esig.dss.simplereport.SimpleReport;
import eu.europa.esig.dss.validation.reports.Reports;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Translates DSS validation reports into SignCheck's API types. This is the only place that reads DSS
 * reports, which keeps the REST contract independent from DSS upgrades.
 *
 * <h2>DSS reports</h2>
 * DSS produces several views of the same validation:
 * <ul>
 *   <li><b>Simple report</b>: the verdict per signature (indication, sub-indication, level, signer,
 *       times, errors and warnings). Our verdict comes from here.</li>
 *   <li><b>Diagnostic data</b>: every raw fact extracted from the document (certificates, revocation
 *       data, timestamps). Used for details such as the signer's certificate fields.</li>
 *   <li><b>Detailed report</b>: every check of the ETSI EN 319 102-1 validation process.</li>
 *   <li><b>ETSI validation report</b>: the standard XML format of ETSI TS 119 102-2.</li>
 * </ul>
 */
@Component
public class ValidationReportMapper {

    private final VerdictMessages verdictMessages;

    public ValidationReportMapper(VerdictMessages verdictMessages) {
        this.verdictMessages = verdictMessages;
    }

    /**
     * @param fileName          name of the validated file
     * @param reports           DSS output; must contain at least one signature
     * @param trustMode         trust mode used for the validation
     * @param includeRawReports whether to add the DSS XML reports
     * @param locale            language of the plain-language reasons
     */
    public ValidationResponse toResponse(String fileName, Reports reports, TrustMode trustMode,
            boolean includeRawReports, Locale locale) {
        SimpleReport simple = reports.getSimpleReport();
        DiagnosticData diagnostic = reports.getDiagnosticData();

        List<SignatureResult> signatures = simple.getSignatureIdList().stream()
                .map(id -> toSignature(id, simple, diagnostic, locale))
                .toList();

        return new ValidationResponse(
                fileName,
                signatures.isEmpty() ? null : signatures.getFirst().format(),
                toInstant(simple.getValidationTime()),
                trustMode,
                summarise(signatures),
                signatures,
                includeRawReports ? toRawReports(reports) : null);
    }

    static RawReports toRawReports(Reports reports) {
        return new RawReports(reports.getXmlSimpleReport(), reports.getXmlDetailedReport(),
                reports.getXmlDiagnosticData(), reports.getXmlValidationReport());
    }

    /** Maps a DSS main indication to our verdict (SPEC §4 "Verdict mapping"). */
    static Verdict toVerdict(Indication indication) {
        return switch (indication) {
            case TOTAL_PASSED, PASSED -> Verdict.VALID;
            case TOTAL_FAILED, FAILED -> Verdict.INVALID;
            default -> Verdict.INDETERMINATE;
        };
    }

    /** {@code VALID} only if all signatures are; otherwise the worst verdict found. */
    static ValidationSummary summarise(List<SignatureResult> signatures) {
        int valid = (int) signatures.stream().filter(s -> s.verdict() == Verdict.VALID).count();
        Verdict overall;
        if (!signatures.isEmpty() && valid == signatures.size()) {
            overall = Verdict.VALID;
        } else if (signatures.stream().anyMatch(s -> s.verdict() == Verdict.INVALID)) {
            overall = Verdict.INVALID;
        } else {
            overall = Verdict.INDETERMINATE;
        }
        return new ValidationSummary(signatures.size(), valid, overall);
    }

    private SignatureResult toSignature(String id, SimpleReport simple, DiagnosticData diagnostic, Locale locale) {
        SignatureWrapper signature = diagnostic.getSignatureById(id);
        CertificateWrapper signingCertificate = signature == null ? null : signature.getSigningCertificate();
        SignatureLevel level = simple.getSignatureFormat(id);
        SubIndication subIndication = simple.getSubIndication(id);
        SignatureQualification qualification = simple.getSignatureQualification(id);

        return new SignatureResult(
                id,
                toVerdict(simple.getIndication(id)),
                subIndication == null ? null : subIndication.name(),
                verdictMessages.reason(subIndication, locale),
                level == null ? null : level.getSignatureForm().name(),
                level == null ? null : level.toString(),
                qualification == null ? null : qualification.getReadable(),
                toSigner(signingCertificate),
                signingCertificate == null ? null : signingCertificate.getCertificateIssuerDN(),
                toInstant(simple.getSigningTime(id)),
                toInstant(simple.getBestSignatureTime(id)),
                signature == null ? List.of() : toChain(signature.getCertificateChain()),
                toRevocation(signingCertificate),
                signature == null ? List.of() : toTimestamps(signature.getTimestampList(), simple),
                texts(simple.getAdESValidationWarnings(id)),
                texts(simple.getAdESValidationErrors(id)));
    }

    private static SignerInfo toSigner(CertificateWrapper certificate) {
        if (certificate == null) {
            return null;
        }
        return new SignerInfo(
                certificate.getCommonName(),
                certificate.getOrganizationName(),
                certificate.getCountryName(),
                hexSerial(certificate.getSerialNumber()));
    }

    static List<CertificateInfo> toChain(List<CertificateWrapper> chain) {
        return chain.stream()
                .map(certificate -> new CertificateInfo(
                        certificate.getCertificateDN(),
                        certificate.getCertificateIssuerDN(),
                        toInstant(certificate.getNotBefore()),
                        toInstant(certificate.getNotAfter()),
                        certificate.isTrusted()))
                .toList();
    }

    /** The most recently produced CRL / OCSP response DSS collected for the certificate. */
    static RevocationInfo toRevocation(CertificateWrapper certificate) {
        if (certificate == null) {
            return null;
        }
        return certificate.getCertificateRevocationData().stream()
                .max(Comparator.comparing(CertificateRevocationWrapper::getProductionDate,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(revocation -> new RevocationInfo(
                        revocation.getStatus() == null ? null : revocation.getStatus().name(),
                        revocation.getRevocationType() == null ? null : revocation.getRevocationType().name(),
                        toInstant(revocation.getProductionDate())))
                .orElse(null);
    }

    static List<TimestampInfo> toTimestamps(List<TimestampWrapper> timestamps, SimpleReport simple) {
        return timestamps.stream()
                .map(timestamp -> {
                    CertificateWrapper tsa = timestamp.getSigningCertificate();
                    Indication indication = simple.getIndication(timestamp.getId());
                    return new TimestampInfo(
                            timestamp.getType() == null ? null : timestamp.getType().name(),
                            toInstant(timestamp.getProductionTime()),
                            tsa == null ? null : tsa.getCommonName(),
                            indication != null && toVerdict(indication) == Verdict.VALID);
                })
                .toList();
    }

    private static String hexSerial(String decimal) {
        if (decimal == null || decimal.isBlank()) {
            return null;
        }
        return new BigInteger(decimal).toString(16).toUpperCase(Locale.ROOT);
    }

    private static List<String> texts(List<Message> messages) {
        return messages == null ? List.of() : messages.stream().map(Message::getValue).distinct().toList();
    }

    private static Instant toInstant(Date date) {
        return date == null ? null : date.toInstant();
    }
}
