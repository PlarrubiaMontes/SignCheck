package dev.signcheck.testpki;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

/**
 * A certificate, its private key and its chain up to (and including) the root, as produced by
 * {@link TestPki}. TEST-ONLY material, generated in memory.
 *
 * @param certificate the end-entity or CA certificate
 * @param privateKey  the matching private key
 * @param chain       certificate first, then its issuers, ending with the self-signed root
 */
public record TestCredential(X509Certificate certificate, PrivateKey privateKey, List<X509Certificate> chain) {

    public TestCredential {
        chain = List.copyOf(chain);
    }

    /** Subject distinguished name in RFC 2253 form, convenient for assertions. */
    public String subject() {
        return certificate.getSubjectX500Principal().getName();
    }
}
