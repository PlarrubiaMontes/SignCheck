package dev.signcheck.testpki;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Writes test material that the application reads from disk (PKCS#12 trust stores) into a temporary
 * directory, once per JVM. Nothing is written inside the repository.
 */
public final class TestPkiFiles {

    /** TEST-ONLY password of every generated keystore. Not a secret. */
    public static final String PASSWORD = "test-only-password";

    private static final Map<String, Path> CREATED = new ConcurrentHashMap<>();

    private TestPkiFiles() {
    }

    /**
     * PKCS#12 file holding the given certificates as trusted entries.
     *
     * @param name         unique name of the store (reused if already written in this JVM)
     * @param certificates certificates to store
     * @return the file location as a {@code file:} URI string, ready for a Spring {@code Resource} property
     */
    public static String trustStore(String name, X509Certificate... certificates) {
        return CREATED.computeIfAbsent(name, key -> write(key, certificates)).toUri().toString();
    }

    /** A fresh empty temporary directory (e.g. for the Trusted Lists cache). */
    public static String tempDirectory(String prefix) {
        try {
            return Files.createTempDirectory(prefix).toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path write(String name, X509Certificate[] certificates) {
        try {
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(null, null);
            for (int i = 0; i < certificates.length; i++) {
                store.setCertificateEntry(name + "-" + i, certificates[i]);
            }
            Path file = Files.createTempFile("signcheck-" + name + "-", ".p12");
            file.toFile().deleteOnExit();
            try (OutputStream out = Files.newOutputStream(file)) {
                store.store(out, PASSWORD.toCharArray());
            }
            return file;
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Unable to write test keystore " + name, e);
        }
    }
}
