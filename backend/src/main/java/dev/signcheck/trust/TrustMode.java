package dev.signcheck.trust;

/**
 * Where the validator gets its <em>trust anchors</em> from.
 *
 * <p>A signature can only be {@code VALID} if the signer's certificate chain ends in a certificate
 * the validator trusts (a trust anchor). Under eIDAS, the authoritative trust anchors are the
 * qualified trust service providers published in the national Trusted Lists, which are themselves
 * referenced by the EU List of Trusted Lists (LOTL).
 *
 * <ul>
 *   <li>{@link #NONE} — no trust anchors. Cryptographic checks still run, but chains can never be
 *       trusted, so results are at best {@code INDETERMINATE}. Offline; meant for local development.</li>
 *   <li>{@link #KEYSTORE} — trust anchors loaded from a configured PKCS#12 keystore (for example the
 *       demo CA used by the signing feature).</li>
 *   <li>{@link #LOTL} — trust anchors downloaded from the EU LOTL and national Trusted Lists, with
 *       online revocation checks (OCSP/CRL). This is the eIDAS-aware production mode.</li>
 * </ul>
 */
public enum TrustMode {
    NONE,
    KEYSTORE,
    LOTL
}
