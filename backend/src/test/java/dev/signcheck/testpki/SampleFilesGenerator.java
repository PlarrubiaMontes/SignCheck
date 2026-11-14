package dev.signcheck.testpki;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CRLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Not a test: writes TEST-ONLY sample files to {@code target/samples} to try the API by hand
 * (Swagger UI, curl, the frontend). Run with:
 *
 * <pre>./mvnw test -Dtest=SampleFilesGenerator -Dsamples=true</pre>
 *
 * Every certificate is from the {@link TestPki} of that run; files from different runs do not mix.
 */
@EnabledIfSystemProperty(named = "samples", matches = "true")
class SampleFilesGenerator {

    @Test
    void writeSampleFiles() throws IOException, CRLException {
        TestPki pki = TestPki.get();
        Path dir = Files.createDirectories(Path.of("target", "samples"));

        write(dir, "pades-valid.pdf", SignedSamples.padesBaselineB(pki.valid()));
        write(dir, "pades-valid-with-timestamp.pdf", SignedSamples.padesBaselineT(pki.valid()));
        write(dir, "pades-tampered.pdf", SignedSamples.tamper(SignedSamples.padesBaselineB(pki.valid())));
        write(dir, "pades-untrusted.pdf", SignedSamples.padesBaselineB(pki.untrusted()));
        write(dir, "pades-revoked.pdf", SignedSamples.padesBaselineB(pki.revoked()));
        write(dir, "pades-two-signatures-one-broken.pdf", SignedSamples.corruptSignatureValue(
                SignedSamples.padesBaselineB(SignedSamples.padesBaselineB(pki.valid()), pki.valid(), pki.now()), 1));
        write(dir, "xades-enveloped.xml", SignedSamples.xadesEnveloped(pki.valid()));
        write(dir, "xades-detached.xml", SignedSamples.xadesDetached(pki.valid()));
        write(dir, "cades-attached.p7m", SignedSamples.cadesAttached(pki.valid()));
        write(dir, "cades-detached.p7s", SignedSamples.cadesDetached(pki.valid()));
        write(dir, SignedSamples.DETACHED_ORIGINAL_NAME, SampleDocuments.text());
        write(dir, "unsigned.pdf", SampleDocuments.pdf());

        // Trust material for KEYSTORE mode (the CRLs are only reachable through the tests' in-memory network)
        Files.copy(Path.of(java.net.URI.create(TestPkiFiles.trustStore("test-root", pki.root().certificate()))),
                dir.resolve("test-root.p12"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        write(dir, "intermediate.crl", pki.crl().getEncoded());
    }

    private static void write(Path dir, String name, byte[] content) throws IOException {
        Files.write(dir.resolve(name), content);
    }
}
