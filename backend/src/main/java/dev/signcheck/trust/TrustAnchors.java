package dev.signcheck.trust;

import dev.signcheck.config.SignCheckProperties;
import dev.signcheck.signing.DemoCertificateAuthority;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.spi.tsl.TrustedListsCertificateSource;
import eu.europa.esig.dss.spi.x509.CertificateSource;
import eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The set of certificates the validator trusts, built once at startup from {@link TrustMode}.
 *
 * <h2>Trust anchors</h2>
 * A certificate chain is only meaningful if it ends in a certificate the relying party trusts
 * <em>a priori</em>: a trust anchor. Signatures whose chain does not reach one can be mathematically
 * correct but never {@code VALID} (DSS reports {@code NO_CERTIFICATE_CHAIN_FOUND}).
 * <ul>
 *   <li>{@link TrustMode#NONE}: no anchors.</li>
 *   <li>{@link TrustMode#KEYSTORE}: the certificates of a configured PKCS#12 file (fails fast if missing).</li>
 *   <li>{@link TrustMode#LOTL}: the services listed in the EU Trusted Lists, loaded in the background by
 *       {@link TrustListService} into a {@link TrustedListsCertificateSource}, plus the keystore if present.
 *       Trusted Lists also carry the <em>qualification</em> of each service (e.g. "issues qualified
 *       certificates"), which DSS uses to decide whether a signature is a qualified electronic signature.</li>
 * </ul>
 * When signing is enabled, the root of the TEST-ONLY {@link DemoCertificateAuthority} is trusted as well (in every
 * mode), so documents signed by the demo can be validated as {@code VALID}.
 */
@Component
public class TrustAnchors {

    private final TrustMode mode;
    private final CommonTrustedCertificateSource keystoreSource;
    private final TrustedListsCertificateSource trustedLists;
    private final CommonTrustedCertificateSource demoCaSource;

    /** Without demo CA (tests, signing disabled). */
    public TrustAnchors(SignCheckProperties properties) {
        this(properties, (DemoCertificateAuthority) null);
    }

    @Autowired
    public TrustAnchors(SignCheckProperties properties, ObjectProvider<DemoCertificateAuthority> demoCa) {
        this(properties, demoCa.getIfAvailable());
    }

    public TrustAnchors(SignCheckProperties properties, DemoCertificateAuthority demoCa) {
        SignCheckProperties.Trust trust = properties.trust();
        this.mode = trust.mode();
        this.keystoreSource = switch (mode) {
            case NONE -> null;
            case KEYSTORE -> KeystoreCertificates.load(trust.keystore(), "signcheck.trust.keystore");
            case LOTL -> KeystoreCertificates.exists(trust.keystore())
                    ? KeystoreCertificates.load(trust.keystore(), "signcheck.trust.keystore")
                    : null;
        };
        this.trustedLists = mode == TrustMode.LOTL ? new TrustedListsCertificateSource() : null;
        if (demoCa == null) {
            this.demoCaSource = null;
        } else {
            this.demoCaSource = new CommonTrustedCertificateSource();
            demoCaSource.addCertificate(new CertificateToken(demoCa.root()));
        }
    }

    public TrustMode mode() {
        return mode;
    }

    /** Every trusted certificate source of the current mode (plus the demo CA when signing is enabled). */
    public List<CertificateSource> sources() {
        List<CertificateSource> sources = new ArrayList<>(3);
        if (trustedLists != null) {
            sources.add(trustedLists);
        }
        if (keystoreSource != null) {
            sources.add(keystoreSource);
        }
        if (demoCaSource != null) {
            sources.add(demoCaSource);
        }
        return List.copyOf(sources);
    }

    /** The Trusted Lists source, present only in {@link TrustMode#LOTL}. */
    public Optional<TrustedListsCertificateSource> trustedLists() {
        return Optional.ofNullable(trustedLists);
    }

    /** Number of trusted certificates currently known (grows when Trusted Lists finish loading). */
    public int trustedCertificateCount() {
        return sources().stream().mapToInt(source -> source.getCertificates().size()).sum();
    }
}
