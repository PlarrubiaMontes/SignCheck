package dev.signcheck.testpki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.security.cert.CertPathValidator;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CRLEntry;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class TestPkiTest {

    private static final int DIGITAL_SIGNATURE = 0;
    private static final int NON_REPUDIATION = 1;
    private static final int KEY_ENCIPHERMENT = 2;
    private static final int KEY_CERT_SIGN = 5;
    private static final int CRL_SIGN = 6;

    private final TestPki pki = TestPki.get();

    @Test
    void shouldBuildThePkiOncePerJvm() {
        assertThat(TestPki.get()).isSameAs(pki);
    }

    @Test
    void shouldMarkEveryCertificateAsTest() {
        assertThat(allCredentials()).allSatisfy(credential -> assertThat(credential.subject()).contains("TEST"));
    }

    @Test
    void shouldMakeRootsSelfSignedCertificateAuthorities() throws Exception {
        for (TestCredential root : List.of(pki.root(), pki.untrustedRoot())) {
            X509Certificate cert = root.certificate();
            assertThat(cert.getSubjectX500Principal()).isEqualTo(cert.getIssuerX500Principal());
            cert.verify(cert.getPublicKey());
            assertThat(cert.getBasicConstraints()).as("CA flag").isGreaterThanOrEqualTo(0);
            assertThat(cert.getKeyUsage()[KEY_CERT_SIGN]).isTrue();
            assertThat(cert.getKeyUsage()[CRL_SIGN]).isTrue();
        }
    }

    @Test
    void shouldIssueTheIntermediateFromTheRootWithPathLengthZero() throws Exception {
        X509Certificate intermediate = pki.intermediate().certificate();

        intermediate.verify(pki.root().certificate().getPublicKey());
        assertThat(intermediate.getBasicConstraints()).isZero();
        assertThat(pki.intermediate().chain()).containsExactly(intermediate, pki.root().certificate());
    }

    @ParameterizedTest
    @MethodSource("signersIssuedByTheIntermediate")
    void shouldIssueSignersFromTheIntermediateAsEndEntities(TestCredential signer) throws Exception {
        X509Certificate cert = signer.certificate();

        cert.verify(pki.intermediate().certificate().getPublicKey());
        assertThat(cert.getBasicConstraints()).as("not a CA").isEqualTo(-1);
        assertThat(signer.chain())
                .containsExactly(cert, pki.intermediate().certificate(), pki.root().certificate());
    }

    @Test
    void shouldBuildAPkixValidChainForTheValidSigner() throws Exception {
        assertThatNoException().isThrownBy(() -> pkixValidate(pki.valid(), pki.root()));
    }

    @Test
    void shouldNotChainTheUntrustedSignerToTheTrustedRoot() throws Exception {
        pki.untrusted().certificate().verify(pki.untrustedRoot().certificate().getPublicKey());

        assertThatThrownBy(() -> pkixValidate(pki.untrusted(), pki.root()))
                .isInstanceOf(CertPathValidatorException.class);
    }

    @Test
    void shouldAllowSignaturesOnlyForSigningCertificates() {
        boolean[] usage = pki.valid().certificate().getKeyUsage();
        assertThat(usage[DIGITAL_SIGNATURE]).isTrue();
        assertThat(usage[NON_REPUDIATION]).isTrue();

        boolean[] wrong = pki.wrongKeyUsage().certificate().getKeyUsage();
        assertThat(wrong[DIGITAL_SIGNATURE]).isFalse();
        assertThat(wrong[NON_REPUDIATION]).isFalse();
        assertThat(wrong[KEY_ENCIPHERMENT]).isTrue();
    }

    @Test
    void shouldHaveTheExpectedValidityPeriods() {
        Date now = Date.from(pki.now());

        assertThatNoException().isThrownBy(() -> pki.valid().certificate().checkValidity(now));
        assertThatNoException().isThrownBy(() -> pki.revoked().certificate().checkValidity(now));
        assertThatThrownBy(() -> pki.expired().certificate().checkValidity(now))
                .isInstanceOf(CertificateExpiredException.class);
        assertThatThrownBy(() -> pki.notYetValid().certificate().checkValidity(now))
                .isInstanceOf(CertificateNotYetValidException.class);
    }

    @Test
    void shouldListOnlyTheRevokedSignerInTheCrl() throws Exception {
        var crl = pki.crl();

        crl.verify(pki.intermediate().certificate().getPublicKey());
        assertThat(crl.getIssuerX500Principal()).isEqualTo(pki.intermediate().certificate().getSubjectX500Principal());
        assertThat(crl.isRevoked(pki.revoked().certificate())).isTrue();
        assertThat(crl.isRevoked(pki.valid().certificate())).isFalse();

        X509CRLEntry entry = crl.getRevokedCertificate(pki.revoked().certificate());
        assertThat(entry.getRevocationDate().toInstant()).isEqualTo(pki.revocationTime());
        assertThat(crl.getNextUpdate()).isAfter(Date.from(pki.now()));
    }

    @Test
    void shouldRestrictTheTsaCertificateToTimeStamping() throws Exception {
        X509Certificate tsa = pki.tsa().certificate();

        assertThat(tsa.getExtendedKeyUsage()).containsExactly("1.3.6.1.5.5.7.3.8");
        assertThat(tsa.getCriticalExtensionOIDs()).contains("2.5.29.37");
    }

    @Test
    void shouldPublishAnEmptyRootCrlCoveringTheIntermediate() throws Exception {
        var rootCrl = pki.rootCrl();

        rootCrl.verify(pki.root().certificate().getPublicKey());
        assertThat(rootCrl.getRevokedCertificates()).isNull();
        assertThat(rootCrl.isRevoked(pki.intermediate().certificate())).isFalse();
    }

    @Test
    void shouldPairEveryCertificateWithItsPrivateKey() throws Exception {
        for (TestCredential credential : allCredentials()) {
            var signature = java.security.Signature.getInstance("SHA256withECDSA");
            byte[] data = credential.subject().getBytes();
            signature.initSign(credential.privateKey());
            signature.update(data);
            byte[] signed = signature.sign();

            signature.initVerify(credential.certificate().getPublicKey());
            signature.update(data);
            assertThat(signature.verify(signed)).as(credential.subject()).isTrue();
        }
    }

    static Stream<TestCredential> signersIssuedByTheIntermediate() {
        TestPki pki = TestPki.get();
        return Stream.of(pki.valid(), pki.expired(), pki.notYetValid(), pki.revoked(), pki.wrongKeyUsage(), pki.tsa());
    }

    private List<TestCredential> allCredentials() {
        return List.of(pki.root(), pki.intermediate(), pki.valid(), pki.expired(), pki.notYetValid(),
                pki.revoked(), pki.wrongKeyUsage(), pki.untrustedRoot(), pki.untrusted(), pki.tsa(), pki.lotlSigner(),
                pki.tlSigner());
    }

    private static void pkixValidate(TestCredential signer, TestCredential anchor) throws Exception {
        var factory = CertificateFactory.getInstance("X.509");
        var path = factory.generateCertPath(signer.chain().subList(0, signer.chain().size() - 1));
        var params = new PKIXParameters(Set.of(new TrustAnchor(anchor.certificate(), null)));
        params.setRevocationEnabled(false);
        CertPathValidator.getInstance("PKIX").validate(path, params);
    }
}
