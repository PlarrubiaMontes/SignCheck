package dev.signcheck.config;

import dev.signcheck.trust.TrustAnchors;
import dev.signcheck.trust.TrustMode;
import eu.europa.esig.dss.service.crl.OnlineCRLSource;
import eu.europa.esig.dss.service.ocsp.OnlineOCSPSource;
import eu.europa.esig.dss.spi.validation.CertificateVerifier;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.spi.x509.CertificateSource;
import eu.europa.esig.dss.spi.x509.aia.DefaultAIASource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DSS infrastructure shared by the whole application.
 *
 * <p>The {@link CertificateVerifier} tells DSS where to find what is <em>not</em> inside the signed
 * document: trust anchors, revocation data and missing intermediate certificates. It holds configuration
 * only, so a single instance is shared by all requests.
 *
 * <h2>Revocation</h2>
 * A certificate can be revoked before it expires (lost key, wrong data...). Validators learn it from
 * the issuing CA through a <b>CRL</b> (a signed list of revoked serial numbers, downloaded from the URL
 * in the certificate) or <b>OCSP</b> (a signed real-time answer for one certificate). DSS tries OCSP
 * first and falls back to CRL.
 *
 * <table>
 *   <caption>What each trust mode configures</caption>
 *   <tr><th>Mode</th><th>Trust anchors</th><th>Online OCSP/CRL</th><th>AIA (fetch missing intermediates)</th></tr>
 *   <tr><td>NONE</td><td>none</td><td>no</td><td>no</td></tr>
 *   <tr><td>KEYSTORE</td><td>PKCS#12</td><td>yes</td><td>no</td></tr>
 *   <tr><td>LOTL</td><td>EU Trusted Lists (+ keystore)</td><td>yes</td><td>yes</td></tr>
 * </table>
 * In {@code NONE} nothing can be trusted, so going online would only cost time: it stays fully offline.
 */
@Configuration(proxyBeanMethods = false)
public class DssConfig {

    @Bean
    NetworkDataLoaders networkDataLoaders(SignCheckProperties properties) {
        return NetworkDataLoaders.online(properties.network());
    }

    @Bean
    CertificateVerifier certificateVerifier(TrustAnchors trustAnchors, NetworkDataLoaders loaders) {
        return buildCertificateVerifier(trustAnchors, loaders);
    }

    /** Builds the verifier for the trust mode of {@code trustAnchors} (public for unit tests). */
    public static CertificateVerifier buildCertificateVerifier(TrustAnchors trustAnchors, NetworkDataLoaders loaders) {
        CommonCertificateVerifier verifier = new CommonCertificateVerifier();
        // DSS's default constructor installs an online AIA source with its own HTTP client (no timeouts,
        // outside NetworkDataLoaders). Remove it: AIA is only enabled, through our loaders, in LOTL mode.
        verifier.setAIASource(null);
        if (trustAnchors.mode() == TrustMode.NONE) {
            return verifier;
        }
        verifier.setTrustedCertSources(trustAnchors.sources().toArray(CertificateSource[]::new));
        verifier.setCrlSource(new OnlineCRLSource(loaders.http()));
        verifier.setOcspSource(new OnlineOCSPSource(loaders.ocsp()));
        if (trustAnchors.mode() == TrustMode.LOTL) {
            verifier.setAIASource(new DefaultAIASource(loaders.http()));
        }
        return verifier;
    }
}
