package dev.signcheck.testpki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.europa.esig.dss.enumerations.SignatureForm;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.spi.signature.AdvancedSignature;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class SignedSamplesTest {

    private final TestPki pki = TestPki.get();

    @Test
    void shouldProduceAPadesSignatureThatDssCanParse() {
        byte[] signed = SignedSamples.padesBaselineB(pki.valid());

        List<AdvancedSignature> signatures = signaturesIn(signed);

        assertThat(signatures).hasSize(1);
        AdvancedSignature signature = signatures.getFirst();
        assertThat(signature.getSignatureForm()).isEqualTo(SignatureForm.PAdES);
        assertThat(signature.getSigningTime().toInstant()).isEqualTo(pki.now());
        assertThat(signature.getCertificates())
                .anySatisfy(token -> assertThat(token.getCertificate()).isEqualTo(pki.valid().certificate()));
    }

    @Test
    void shouldAddASecondSignatureWithoutRemovingTheFirst() {
        byte[] once = SignedSamples.padesBaselineB(pki.valid());

        byte[] twice = SignedSamples.padesBaselineB(once, pki.untrusted(), pki.now());

        assertThat(signaturesIn(twice)).hasSize(2);
        assertThat(twice).startsWith(once); // incremental update: original bytes untouched
    }

    @Test
    void shouldSignWithCertificatesARealSignerWouldRefuse() {
        assertThat(signaturesIn(SignedSamples.padesBaselineB(pki.expired()))).hasSize(1);
        assertThat(signaturesIn(SignedSamples.padesBaselineB(pki.notYetValid()))).hasSize(1);
    }

    @Test
    void shouldTamperExactlyOneByteOfTheSignedContent() {
        byte[] signed = SignedSamples.padesBaselineB(pki.valid());

        byte[] tampered = SignedSamples.tamper(signed);

        assertThat(tampered).hasSameSizeAs(signed).isNotEqualTo(signed);
        int differences = 0;
        for (int i = 0; i < signed.length; i++) {
            differences += signed[i] != tampered[i] ? 1 : 0;
        }
        assertThat(differences).isOne();
        assertThat(signaturesIn(tampered)).as("still parseable").hasSize(1);
    }

    @Test
    void shouldRefuseToTamperADocumentWithoutTheMarker() {
        byte[] other = "no marker here".getBytes(StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> SignedSamples.tamper(other)).isInstanceOf(IllegalArgumentException.class);
    }

    private static List<AdvancedSignature> signaturesIn(byte[] document) {
        return SignedDocumentValidator.fromDocument(new InMemoryDocument(document)).getSignatures();
    }
}
