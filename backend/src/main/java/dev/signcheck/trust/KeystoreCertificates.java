package dev.signcheck.trust;

import dev.signcheck.config.SignCheckProperties;
import eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource;
import eu.europa.esig.dss.spi.x509.KeyStoreCertificateSource;
import java.io.IOException;
import java.io.InputStream;

/** Loads the certificates of a PKCS#12 keystore configured in {@link SignCheckProperties}. */
final class KeystoreCertificates {

    private static final String PKCS12 = "PKCS12";

    private KeystoreCertificates() {
    }

    static boolean exists(SignCheckProperties.Keystore keystore) {
        return keystore != null && keystore.path() != null && keystore.path().exists();
    }

    /** Loads the keystore certificates as plain certificate source (used e.g. to verify LOTL signers). */
    static KeyStoreCertificateSource read(SignCheckProperties.Keystore keystore, String property) {
        if (!exists(keystore)) {
            throw new IllegalStateException("Keystore not found: set " + property + ".path to an existing PKCS#12 file"
                    + (keystore == null || keystore.path() == null ? "" : " (current: " + keystore.path() + ")"));
        }
        char[] password = keystore.password() == null ? new char[0] : keystore.password().toCharArray();
        try (InputStream in = keystore.path().getInputStream()) {
            return new KeyStoreCertificateSource(in, PKCS12, password);
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Unable to read the keystore configured in " + property, e);
        }
    }

    /** Loads the keystore certificates as trust anchors. */
    static CommonTrustedCertificateSource load(SignCheckProperties.Keystore keystore, String property) {
        CommonTrustedCertificateSource trusted = new CommonTrustedCertificateSource();
        trusted.importAsTrusted(read(keystore, property));
        return trusted;
    }
}
