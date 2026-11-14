package dev.signcheck.trust;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.signcheck.config.DssConfig;
import dev.signcheck.config.NetworkDataLoaders;
import dev.signcheck.config.SignCheckProperties;
import dev.signcheck.testpki.OfflineNetwork;
import dev.signcheck.testpki.TestProperties;
import eu.europa.esig.dss.spi.validation.CertificateVerifier;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * How each {@link TrustMode} builds the trust anchors and the DSS certificate verifier, tested without a
 * Spring context. End-to-end behaviour per mode is covered by the API tests (KEYSTORE), {@code LotlTrustModeTest}
 * (LOTL) and {@code TrustModeNoneValidationTest} (NONE).
 */
class TrustModeConfigurationTest {

    private static final String ROOT_STORE = TestProperties.ROOT_STORE;
    private static final NetworkDataLoaders NETWORK = OfflineNetwork.loaders(Map.of());

    @Test
    void shouldConfigureNoTrustAndNoNetworkInModeNone() {
        TrustAnchors anchors = new TrustAnchors(properties(TrustMode.NONE, ROOT_STORE));
        CertificateVerifier verifier = DssConfig.buildCertificateVerifier(anchors, NETWORK);

        assertThat(anchors.sources()).isEmpty();
        assertThat(anchors.trustedCertificateCount()).isZero();
        assertThat(verifier.getTrustedCertSources().isEmpty()).isTrue();
        assertThat(verifier.getCrlSource()).isNull();
        assertThat(verifier.getOcspSource()).isNull();
        assertThat(verifier.getAIASource()).isNull();
    }

    @Test
    void shouldTrustTheKeystoreAndCheckRevocationOnlineInModeKeystore() {
        TrustAnchors anchors = new TrustAnchors(properties(TrustMode.KEYSTORE, ROOT_STORE));
        CertificateVerifier verifier = DssConfig.buildCertificateVerifier(anchors, NETWORK);

        assertThat(anchors.trustedCertificateCount()).isOne();
        assertThat(verifier.getTrustedCertSources().getNumberOfCertificates()).isOne();
        assertThat(verifier.getCrlSource()).isNotNull();
        assertThat(verifier.getOcspSource()).isNotNull();
        assertThat(verifier.getAIASource()).as("AIA only in LOTL mode").isNull();
    }

    @Test
    void shouldFailFastWhenTheKeystoreIsMissingInModeKeystore() {
        assertThatThrownBy(() -> new TrustAnchors(properties(TrustMode.KEYSTORE, "file:/does/not/exist.p12")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("signcheck.trust.keystore");
    }

    @Test
    void shouldUseTrustedListsAndFetchMissingIntermediatesInModeLotl() {
        TrustAnchors anchors = new TrustAnchors(properties(TrustMode.LOTL, "file:/does/not/exist.p12"));
        CertificateVerifier verifier = DssConfig.buildCertificateVerifier(anchors, NETWORK);

        assertThat(anchors.trustedLists()).isPresent();
        assertThat(anchors.sources()).as("no keystore configured: only the Trusted Lists").hasSize(1);
        assertThat(verifier.getCrlSource()).isNotNull();
        assertThat(verifier.getOcspSource()).isNotNull();
        assertThat(verifier.getAIASource()).isNotNull();
    }

    @Test
    void shouldAddTheKeystoreToTheTrustedListsInModeLotlWhenItExists() {
        TrustAnchors anchors = new TrustAnchors(properties(TrustMode.LOTL, ROOT_STORE));

        assertThat(anchors.sources()).hasSize(2);
        assertThat(anchors.trustedCertificateCount()).isOne();
    }

    private static SignCheckProperties properties(TrustMode mode, String keystore) {
        return TestProperties.withTrust(mode, keystore);
    }
}
