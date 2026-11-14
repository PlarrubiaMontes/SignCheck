package dev.signcheck.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.signcheck.testpki.SignedSamples;
import dev.signcheck.testpki.TestPki;
import dev.signcheck.testpki.TestProperties;
import dev.signcheck.trust.TrustMode;
import dev.signcheck.validation.dto.CertificateInfo;
import dev.signcheck.validation.dto.SignatureResult;
import dev.signcheck.validation.dto.TimestampInfo;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.spi.validation.CertificateVerifier;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * Field-by-field mapping of real DSS reports (no Spring): a PAdES-BASELINE-T signature by the valid test
 * signer, validated with the production KEYSTORE-mode verifier and the in-memory network.
 */
class ValidationReportMapperFieldsTest {

    private static final TestPki PKI = TestPki.get();
    private static SignatureResult levelT;
    private static SignatureResult levelB;

    @BeforeAll
    static void validateSamples() {
        CertificateVerifier verifier = TestProperties.keystoreModeVerifier();
        levelT = validate(SignedSamples.padesBaselineT(PKI.valid()), verifier);
        levelB = validate(SignedSamples.padesBaselineB(PKI.valid()), verifier);
    }

    @Test
    void shouldMapTheLevelIncludingTheTimestamp() {
        assertThat(levelT.verdict().name()).isEqualTo("VALID");
        assertThat(levelT.level()).isEqualTo("PAdES-BASELINE-T");
    }

    @Test
    void shouldMapTheCertificateChainFromSignerToTrustAnchor() {
        assertThat(levelT.certificateChain())
                .extracting(CertificateInfo::subject)
                .containsExactly(PKI.valid().subject(), PKI.intermediate().subject(), PKI.root().subject());
        assertThat(levelT.certificateChain()).extracting(CertificateInfo::trusted).containsExactly(false, false, true);

        CertificateInfo signer = levelT.certificateChain().getFirst();
        assertThat(signer.issuer()).isEqualTo(PKI.intermediate().subject());
        assertThat(signer.notBefore()).isEqualTo(PKI.valid().certificate().getNotBefore().toInstant());
        assertThat(signer.notAfter()).isEqualTo(PKI.valid().certificate().getNotAfter().toInstant());
    }

    @Test
    void shouldMapTheRevocationStatusOfTheSigningCertificate() {
        assertThat(levelT.revocation()).isNotNull();
        assertThat(levelT.revocation().status()).isEqualTo("GOOD");
        assertThat(levelT.revocation().source()).isEqualTo("CRL");
        assertThat(levelT.revocation().producedAt()).isEqualTo(PKI.crl().getThisUpdate().toInstant());
    }

    @Test
    void shouldMapTheSignatureTimestamp() {
        assertThat(levelT.timestamps()).singleElement().satisfies(timestamp -> {
            assertThat(timestamp.type()).isEqualTo("SIGNATURE_TIMESTAMP");
            assertThat(timestamp.tsaName()).isEqualTo("SignCheck TEST TSA");
            assertThat(timestamp.valid()).isTrue();
            assertThat(timestamp.productionTime()).isCloseTo(PKI.now(), within(5, ChronoUnit.MINUTES));
        });
    }

    @Test
    void shouldUseTheTimestampAsBestSignatureTime() {
        TimestampInfo timestamp = levelT.timestamps().getFirst();

        assertThat(levelT.bestSignatureTime()).isEqualTo(timestamp.productionTime());
    }

    @Test
    void shouldHaveNoTimestampsAndUseValidationTimeForLevelB() {
        assertThat(levelB.timestamps()).isEmpty();
        assertThat(levelB.bestSignatureTime()).isAfter(levelB.claimedSigningTime());
    }

    @Test
    void shouldMapAQualificationThatIsNotQualifiedOutsideTheTrustedLists() {
        // Qualification comes from the EU Trusted Lists; with a plain keystore DSS cannot assert QES
        assertThat(levelT.qualification()).isNotBlank().isNotEqualTo("QESig");
    }

    private static SignatureResult validate(byte[] document, CertificateVerifier verifier) {
        SignedDocumentValidator validator = SignedDocumentValidator.fromDocument(new InMemoryDocument(document));
        validator.setCertificateVerifier(verifier);
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        return new ValidationReportMapper(new VerdictMessages(messages))
                .toResponse("sample.pdf", validator.validateDocument(), TrustMode.KEYSTORE, false, Locale.ENGLISH)
                .signatures().getFirst();
    }
}
