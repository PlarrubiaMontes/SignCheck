package dev.signcheck.testpki;

import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.CRLNumber;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v2CRLBuilder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * A small, self-contained <em>public key infrastructure</em> generated in memory for tests.
 * Nothing here is real: every certificate carries {@code TEST} in its subject and lives only for
 * the duration of the JVM.
 *
 * <h2>Why a PKI?</h2>
 * An advanced electronic signature is only as trustworthy as the certificate that identifies the
 * signer. A validator checks that the signer's certificate chains up to a <em>trust anchor</em>
 * (a root CA it trusts), that every certificate in the chain was valid at signing time, that it
 * allows digital signatures (key usage) and that it has not been revoked (CRL or OCSP).
 * To test all those outcomes offline we need a PKI whose every certificate we control.
 *
 * <h2>Hierarchy</h2>
 * <pre>
 * Root CA (trust anchor in tests) ─── CRL of its CAs (empty)
 * └── Intermediate CA ─── CRL listing {@link #revoked()}
 *     ├── {@link #valid()}          usable signer
 *     ├── {@link #expired()}        validity ended a year ago
 *     ├── {@link #notYetValid()}    validity starts in a year
 *     ├── {@link #revoked()}        revoked yesterday (keyCompromise)
 *     ├── {@link #wrongKeyUsage()}  only keyEncipherment: not allowed to sign
 *     └── {@link #tsa()}            time-stamping authority (EKU timeStamping, critical)
 *
 * Untrusted Root CA (never configured as trust anchor)
 * └── {@link #untrusted()}          cryptographically fine, but its chain ends in an unknown root
 *
 * {@link #lotlSigner()}   self-signed, signs the test List of Trusted Lists (LOTL)
 * {@link #tlSigner()}     self-signed, signs the test national Trusted List
 * </pre>
 *
 * <p>Keys are ECDSA P-256 (fast to generate, supported by every signature format) and certificates
 * are signed with SHA256withECDSA. Dates are relative to {@link #now()} (truncated to seconds),
 * so the PKI is identical in structure on every run while staying valid whenever the tests run.
 *
 * <p>The PKI is built once per JVM ({@link #get()}) because key generation is the expensive part.
 */
public final class TestPki {

    /** CRL distribution point of the signers (unresolvable: RFC 2606 {@code .test} TLD). */
    public static final String CRL_URL = "http://crl.signcheck.test/intermediate.crl";
    /** CRL distribution point of the intermediate CA, published by the root (unresolvable). */
    public static final String ROOT_CRL_URL = "http://crl.signcheck.test/root.crl";

    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";
    private static final BouncyCastleProvider BC = new BouncyCastleProvider();

    private static volatile TestPki instance;

    private final AtomicLong serials = new AtomicLong(1000);
    private final Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private final TestCredential root;
    private final TestCredential intermediate;
    private final TestCredential valid;
    private final TestCredential expired;
    private final TestCredential notYetValid;
    private final TestCredential revoked;
    private final TestCredential wrongKeyUsage;
    private final TestCredential untrustedRoot;
    private final TestCredential untrusted;
    private final TestCredential tsa;
    private final TestCredential lotlSigner;
    private final TestCredential tlSigner;
    private final Instant revocationTime;
    private final X509CRL crl;
    private final X509CRL rootCrl;

    private TestPki() {
        try {
            root = selfSignedCa(dn("SignCheck TEST Root CA"));
            intermediate = subCa(dn("SignCheck TEST Intermediate CA"), root);

            Duration day = Duration.ofDays(1);
            Duration year = Duration.ofDays(365);
            valid = signer(dn("TEST SIGNER Valid"), now.minus(day), now.plus(year), signingUsage());
            expired = signer(dn("TEST SIGNER Expired"),
                    now.minus(year.multipliedBy(2)), now.minus(year), signingUsage());
            notYetValid = signer(dn("TEST SIGNER Not Yet Valid"),
                    now.plus(year), now.plus(year.multipliedBy(2)), signingUsage());
            revoked = signer(dn("TEST SIGNER Revoked"),
                    now.minus(year), now.plus(year), signingUsage());
            wrongKeyUsage = signer(dn("TEST SIGNER Wrong Key Usage"),
                    now.minus(day), now.plus(year), new KeyUsage(KeyUsage.keyEncipherment));

            untrustedRoot = selfSignedCa(dn("SignCheck TEST Untrusted Root CA"));
            untrusted = issue(dn("TEST SIGNER Untrusted"), untrustedRoot,
                    now.minus(day), now.plus(year), signingUsage(), false);

            tsa = timestampingAuthority(dn("SignCheck TEST TSA"), now.minus(year), now.plus(year));

            lotlSigner = trustedListSigner(dn("SignCheck TEST LOTL Signer"));
            tlSigner = trustedListSigner(dn("SignCheck TEST TL Signer"));

            revocationTime = now.minus(day);
            crl = buildCrl(intermediate, List.of(revoked));
            rootCrl = buildCrl(root, List.of());
        } catch (GeneralSecurityException | OperatorCreationException | IOException e) {
            throw new IllegalStateException("Unable to build the test PKI", e);
        }
    }

    /** The shared PKI, generated on first use. */
    public static TestPki get() {
        TestPki local = instance;
        if (local == null) {
            synchronized (TestPki.class) {
                local = instance;
                if (local == null) {
                    local = new TestPki();
                    instance = local;
                }
            }
        }
        return local;
    }

    /** Reference time all validity periods are computed from. */
    public Instant now() {
        return now;
    }

    /** Self-signed root CA; the trust anchor in tests that need a {@code VALID} result. */
    public TestCredential root() {
        return root;
    }

    /** CA issued by {@link #root()}; issues every test signer and the {@link #crl()}. */
    public TestCredential intermediate() {
        return intermediate;
    }

    /** Signer whose certificate is currently valid, trusted and not revoked. */
    public TestCredential valid() {
        return valid;
    }

    /** Signer whose certificate expired one year ago. */
    public TestCredential expired() {
        return expired;
    }

    /** Signer whose certificate only becomes valid in one year. */
    public TestCredential notYetValid() {
        return notYetValid;
    }

    /** Signer whose certificate was revoked at {@link #revocationTime()} (listed in {@link #crl()}). */
    public TestCredential revoked() {
        return revoked;
    }

    /** Signer whose certificate only allows key encipherment, not digital signatures. */
    public TestCredential wrongKeyUsage() {
        return wrongKeyUsage;
    }

    /** Root CA that is never configured as a trust anchor. */
    public TestCredential untrustedRoot() {
        return untrustedRoot;
    }

    /** Signer issued by {@link #untrustedRoot()}. */
    public TestCredential untrusted() {
        return untrusted;
    }

    /** Time-stamping authority issued by {@link #intermediate()}, used for level-T signatures. */
    public TestCredential tsa() {
        return tsa;
    }

    /** Signs the test LOTL; plays the role of the certificates announced in the Official Journal. */
    public TestCredential lotlSigner() {
        return lotlSigner;
    }

    /** Signs the test national Trusted List; announced in the test LOTL. */
    public TestCredential tlSigner() {
        return tlSigner;
    }

    /** When {@link #revoked()} was revoked. */
    public Instant revocationTime() {
        return revocationTime;
    }

    /** CRL issued by {@link #intermediate()} listing {@link #revoked()}; valid for 7 days. */
    public X509CRL crl() {
        return crl;
    }

    /**
     * Empty CRL issued by {@link #root()}, covering the {@link #intermediate()}. Validators also
     * check that the CA which signs the signer's CRL is itself not revoked.
     */
    public X509CRL rootCrl() {
        return rootCrl;
    }

    // ------------------------------------------------------------------------------------------

    private TestCredential selfSignedCa(String dn) throws GeneralSecurityException, OperatorCreationException,
            IOException {
        KeyPair keys = newKeyPair();
        X500Name name = new X500Name(dn);
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(name, nextSerial(),
                Date.from(now.minus(Duration.ofDays(3650))), Date.from(now.plus(Duration.ofDays(3650))),
                name, keys.getPublic());
        JcaX509ExtensionUtils ext = new JcaX509ExtensionUtils();
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        builder.addExtension(Extension.subjectKeyIdentifier, false, ext.createSubjectKeyIdentifier(keys.getPublic()));
        X509Certificate cert = toCertificate(builder.build(signer(keys.getPrivate())));
        return new TestCredential(cert, keys.getPrivate(), List.of(cert));
    }

    /** RFC 3161 requires the TSA certificate to carry only the timeStamping extended key usage, as critical. */
    private TestCredential timestampingAuthority(String dn, Instant notBefore, Instant notAfter)
            throws GeneralSecurityException, OperatorCreationException, IOException {
        KeyPair keys = newKeyPair();
        X509v3CertificateBuilder builder = builder(dn, intermediate, keys.getPublic(), notBefore, notAfter);
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
        builder.addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));
        builder.addExtension(Extension.cRLDistributionPoints, false, crlDistributionPoint(CRL_URL));
        return credential(builder, keys, intermediate);
    }

    /** Self-signed certificate with the Trusted List signing extended key usage (ETSI TS 119 612, 0.4.0.2231.3.0). */
    private TestCredential trustedListSigner(String dn) throws GeneralSecurityException, OperatorCreationException,
            IOException {
        KeyPair keys = newKeyPair();
        X500Name name = new X500Name(dn);
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(name, nextSerial(),
                Date.from(now.minus(Duration.ofDays(365))), Date.from(now.plus(Duration.ofDays(1825))),
                name, keys.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation));
        builder.addExtension(Extension.extendedKeyUsage, false,
                new ExtendedKeyUsage(KeyPurposeId.getInstance(new ASN1ObjectIdentifier("0.4.0.2231.3.0"))));
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                new JcaX509ExtensionUtils().createSubjectKeyIdentifier(keys.getPublic()));
        X509Certificate cert = toCertificate(builder.build(signer(keys.getPrivate())));
        return new TestCredential(cert, keys.getPrivate(), List.of(cert));
    }

    private TestCredential subCa(String dn, TestCredential issuer) throws GeneralSecurityException,
            OperatorCreationException, IOException {
        KeyPair keys = newKeyPair();
        X509v3CertificateBuilder builder = builder(dn, issuer, keys.getPublic(),
                now.minus(Duration.ofDays(1825)), now.plus(Duration.ofDays(1825)));
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        builder.addExtension(Extension.cRLDistributionPoints, false, crlDistributionPoint(ROOT_CRL_URL));
        return credential(builder, keys, issuer);
    }

    private TestCredential signer(String dn, Instant notBefore, Instant notAfter, KeyUsage keyUsage)
            throws GeneralSecurityException, OperatorCreationException, IOException {
        return issue(dn, intermediate, notBefore, notAfter, keyUsage, true);
    }

    private TestCredential issue(String dn, TestCredential issuer, Instant notBefore, Instant notAfter,
            KeyUsage keyUsage, boolean withCrlDistributionPoint)
            throws GeneralSecurityException, OperatorCreationException, IOException {
        KeyPair keys = newKeyPair();
        X509v3CertificateBuilder builder = builder(dn, issuer, keys.getPublic(), notBefore, notAfter);
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true, keyUsage);
        if (withCrlDistributionPoint) {
            builder.addExtension(Extension.cRLDistributionPoints, false, crlDistributionPoint(CRL_URL));
        }
        return credential(builder, keys, issuer);
    }

    private X509v3CertificateBuilder builder(String dn, TestCredential issuer, PublicKey subjectKey,
            Instant notBefore, Instant notAfter) throws GeneralSecurityException, IOException {
        JcaX509ExtensionUtils ext = new JcaX509ExtensionUtils();
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(issuer.certificate(), nextSerial(),
                Date.from(notBefore), Date.from(notAfter), new X500Name(dn), subjectKey);
        builder.addExtension(Extension.subjectKeyIdentifier, false, ext.createSubjectKeyIdentifier(subjectKey));
        builder.addExtension(Extension.authorityKeyIdentifier, false,
                ext.createAuthorityKeyIdentifier(issuer.certificate()));
        return builder;
    }

    private TestCredential credential(X509v3CertificateBuilder builder, KeyPair keys, TestCredential issuer)
            throws GeneralSecurityException, OperatorCreationException {
        X509Certificate cert = toCertificate(builder.build(signer(issuer.privateKey())));
        List<X509Certificate> chain = new ArrayList<>();
        chain.add(cert);
        chain.addAll(issuer.chain());
        return new TestCredential(cert, keys.getPrivate(), chain);
    }

    private X509CRL buildCrl(TestCredential issuer, List<TestCredential> revokedCredentials)
            throws GeneralSecurityException, OperatorCreationException, IOException {
        X509v2CRLBuilder builder = new X509v2CRLBuilder(
                new X509CertificateHolder(issuer.certificate().getEncoded()).getSubject(),
                Date.from(now.minus(Duration.ofHours(1))));
        builder.setNextUpdate(Date.from(now.plus(Duration.ofDays(7))));
        for (TestCredential credential : revokedCredentials) {
            builder.addCRLEntry(credential.certificate().getSerialNumber(), Date.from(revocationTime),
                    CRLReason.keyCompromise);
        }
        builder.addExtension(Extension.authorityKeyIdentifier, false,
                new JcaX509ExtensionUtils().createAuthorityKeyIdentifier(issuer.certificate()));
        builder.addExtension(Extension.cRLNumber, false, new CRLNumber(BigInteger.ONE));
        X509CRLHolder holder = builder.build(signer(issuer.privateKey()));
        return new JcaX509CRLConverter().setProvider(BC).getCRL(holder);
    }

    private static KeyPair newKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
        return generator.generateKeyPair();
    }

    private static ContentSigner signer(PrivateKey key) throws OperatorCreationException {
        return new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).setProvider(BC).build(key);
    }

    private static X509Certificate toCertificate(X509CertificateHolder holder) throws GeneralSecurityException {
        return new JcaX509CertificateConverter().setProvider(BC).getCertificate(holder);
    }

    /**
     * Distinguished name encoded from the most general to the most specific attribute (C, O, CN),
     * as real CAs do; its RFC 2253 string form therefore reads {@code CN=...,O=...,C=ES}.
     */
    private static String dn(String commonName) {
        return "C=ES,O=SignCheck Test PKI,CN=" + commonName;
    }

    private static CRLDistPoint crlDistributionPoint(String url) {
        GeneralNames names = new GeneralNames(new GeneralName(GeneralName.uniformResourceIdentifier, url));
        return new CRLDistPoint(new DistributionPoint[] {new DistributionPoint(new DistributionPointName(names), null, null)});
    }

    private static KeyUsage signingUsage() {
        // nonRepudiation (a.k.a. contentCommitment) is what qualified signing certificates carry
        return new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation);
    }

    private BigInteger nextSerial() {
        return BigInteger.valueOf(serials.incrementAndGet());
    }
}
