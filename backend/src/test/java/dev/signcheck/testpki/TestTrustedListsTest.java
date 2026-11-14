package dev.signcheck.testpki;

import static org.assertj.core.api.Assertions.assertThat;

import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.spi.signature.AdvancedSignature;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class TestTrustedListsTest {

    private final TestPki pki = TestPki.get();

    @Test
    void shouldProduceALotlSignedByTheLotlSigner() {
        byte[] lotl = TestTrustedLists.lotl(pki.lotlSigner());

        assertThat(new String(lotl)).contains("<tsl:TSLVersionIdentifier>6</", TestTrustedLists.TL_URL);
        assertSignedBy(lotl, pki.lotlSigner());
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 6})
    void shouldProduceATrustedListOfEachSupportedVersion(int version) {
        byte[] tl = TestTrustedLists.tl(version, pki.tlSigner());

        assertThat(new String(tl)).contains("<tsl:TSLVersionIdentifier>" + version + "</");
        assertSignedBy(tl, pki.tlSigner());
    }

    private static void assertSignedBy(byte[] document, TestCredential signer) {
        List<AdvancedSignature> signatures =
                SignedDocumentValidator.fromDocument(new InMemoryDocument(document)).getSignatures();
        assertThat(signatures).singleElement().satisfies(signature -> assertThat(signature.getCertificates())
                .anySatisfy(token -> assertThat(token.getCertificate()).isEqualTo(signer.certificate())));
    }
}
