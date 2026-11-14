package dev.signcheck.signing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DemoCertificateAuthorityTest {

    @TempDir
    Path directory;

    @Test
    void shouldGenerateATestOnlyCaOnFirstUse() throws Exception {
        DemoCertificateAuthority ca = DemoCertificateAuthority.loadOrCreate(directory);

        assertThat(directory.resolve(DemoCertificateAuthority.KEYSTORE_FILE)).exists();
        assertThat(ca.root().getSubjectX500Principal().getName()).contains("TEST ONLY");
        assertThat(ca.signer().getSubjectX500Principal().getName()).contains("TEST ONLY");
        ca.signer().verify(ca.root().getPublicKey());
        ca.signer().checkValidity(new Date());
        assertThat(ca.signer().getKeyUsage()[0]).as("digitalSignature").isTrue();
        assertThat(ca.signerChain()).containsExactly(ca.signer(), ca.root());
    }

    @Test
    void shouldReuseTheSameCaAfterARestart() {
        DemoCertificateAuthority first = DemoCertificateAuthority.loadOrCreate(directory);

        DemoCertificateAuthority second = DemoCertificateAuthority.loadOrCreate(directory);

        assertThat(second.root()).isEqualTo(first.root());
        assertThat(second.signer()).isEqualTo(first.signer());
        assertThat(second.signerKey()).isEqualTo(first.signerKey());
    }

    @Test
    void shouldPublishAFreshEmptyCrl() throws Exception {
        DemoCertificateAuthority ca = DemoCertificateAuthority.loadOrCreate(directory);

        var crl = ca.crl();

        crl.verify(ca.root().getPublicKey());
        assertThat(crl.isRevoked(ca.signer())).isFalse();
        assertThat(crl.getNextUpdate()).isAfter(new Date());
        assertThat(ca.crl()).as("cached while fresh").isSameAs(crl);
    }

    @Test
    void shouldNeverWriteInsideTheRepository() {
        DemoCertificateAuthority.loadOrCreate(directory);

        assertThat(Files.exists(Path.of("src", DemoCertificateAuthority.KEYSTORE_FILE))).isFalse();
    }
}
