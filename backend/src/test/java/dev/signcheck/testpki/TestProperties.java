package dev.signcheck.testpki;

import dev.signcheck.config.DssConfig;
import dev.signcheck.config.SignCheckProperties;
import dev.signcheck.trust.TrustAnchors;
import dev.signcheck.trust.TrustMode;
import eu.europa.esig.dss.spi.validation.CertificateVerifier;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.util.unit.DataSize;

/** Builds {@link SignCheckProperties} and production components without a Spring context. */
public final class TestProperties {

    /** Keystore holding the {@link TestPki} root, as a {@code file:} URI. */
    public static final String ROOT_STORE = TestPkiFiles.trustStore("test-root", TestPki.get().root().certificate());

    private TestProperties() {
    }

    /** Properties with the given trust mode and trust keystore location. */
    public static SignCheckProperties withTrust(TrustMode mode, String keystoreLocation) {
        return new SignCheckProperties(
                new SignCheckProperties.Upload(DataSize.ofMegabytes(20), DataSize.ofMegabytes(60)),
                new SignCheckProperties.Trust(mode, URI.create("http://lotl.test"), URI.create("http://oj.test"),
                        null, Path.of("target/tl-cache"), "0 0 */6 * * *",
                        new SignCheckProperties.Keystore(new DefaultResourceLoader().getResource(keystoreLocation),
                                TestPkiFiles.PASSWORD)),
                new SignCheckProperties.Network(Duration.ofSeconds(1), Duration.ofSeconds(1)),
                new SignCheckProperties.Signing(false, ""),
                new SignCheckProperties.Cors(List.of()));
    }

    /** The production verifier of KEYSTORE mode trusting the test root, with the in-memory network. */
    public static CertificateVerifier keystoreModeVerifier() {
        return DssConfig.buildCertificateVerifier(
                new TrustAnchors(withTrust(TrustMode.KEYSTORE, ROOT_STORE)), OfflineNetwork.loaders(Map.of()));
    }
}
