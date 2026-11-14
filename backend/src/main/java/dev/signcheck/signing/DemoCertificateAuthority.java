package dev.signcheck.signing;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLNumber;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The application's own <b>TEST-ONLY</b> certificate authority, used by the demo signing feature.
 *
 * <p>A real signature needs a certificate issued to the signer by a CA (for a qualified signature, a
 * qualified trust service provider listed in an EU Trusted List). The public demo must never sign with
 * user-supplied keys, so it signs with a demo signer issued by this demo CA instead. Every certificate
 * says {@code TEST ONLY} in its subject so it can never be mistaken for a real one.
 *
 * <p>The keys are generated on first start into {@code signcheck.signing.demo-ca-dir} (outside the
 * repository) and reused afterwards, so documents signed before a restart still validate. The demo CA also
 * publishes an (empty) CRL that the validator uses as revocation information for the demo signer.
 */
public class DemoCertificateAuthority {

    /** TEST-ONLY password of the generated demo keystore. Not a secret: the keystore holds demo keys only. */
    static final char[] KEYSTORE_PASSWORD = "demo-ca-test-only".toCharArray();
    static final String KEYSTORE_FILE = "demo-ca.p12";
    private static final String ROOT_ALIAS = "demo-root";
    private static final String SIGNER_ALIAS = "demo-signer";
    private static final BouncyCastleProvider BC = new BouncyCastleProvider();
    private static final Logger LOG = LoggerFactory.getLogger(DemoCertificateAuthority.class);

    private final X509Certificate root;
    private final PrivateKey rootKey;
    private final X509Certificate signer;
    private final PrivateKey signerKey;
    private volatile X509CRL crl;

    private DemoCertificateAuthority(X509Certificate root, PrivateKey rootKey, X509Certificate signer,
            PrivateKey signerKey) {
        this.root = root;
        this.rootKey = rootKey;
        this.signer = signer;
        this.signerKey = signerKey;
    }

    /** Loads the demo CA from {@code directory}, generating it there on first use. */
    public static DemoCertificateAuthority loadOrCreate(Path directory) {
        Path file = directory.resolve(KEYSTORE_FILE);
        try {
            if (Files.exists(file)) {
                return load(file);
            }
            Files.createDirectories(directory);
            DemoCertificateAuthority created = generate();
            created.store(file);
            LOG.info("Generated a new TEST-ONLY demo CA in {}", directory);
            return created;
        } catch (IOException | GeneralSecurityException | OperatorCreationException e) {
            throw new IllegalStateException("Unable to prepare the demo CA in " + directory, e);
        }
    }

    public X509Certificate root() {
        return root;
    }

    public X509Certificate signer() {
        return signer;
    }

    PrivateKey signerKey() {
        return signerKey;
    }

    /** Signer certificate followed by the root. */
    public List<X509Certificate> signerChain() {
        return List.of(signer, root);
    }

    /** Current CRL of the demo CA (empty; reissued daily so it never goes stale). */
    public X509CRL crl() {
        X509CRL current = crl;
        if (current == null || current.getThisUpdate().toInstant().isBefore(Instant.now().minus(Duration.ofDays(1)))) {
            synchronized (this) {
                current = crl;
                if (current == null
                        || current.getThisUpdate().toInstant().isBefore(Instant.now().minus(Duration.ofDays(1)))) {
                    current = issueCrl();
                    crl = current;
                }
            }
        }
        return current;
    }

    // ------------------------------------------------------------------------------------------

    private static DemoCertificateAuthority generate()
            throws GeneralSecurityException, OperatorCreationException, IOException {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        JcaX509ExtensionUtils ext = new JcaX509ExtensionUtils();

        KeyPair rootKeys = newKeyPair();
        X500Name rootName = new X500Name("C=ES,O=SignCheck Demo,CN=SignCheck DEMO Root CA - TEST ONLY");
        X509v3CertificateBuilder rootBuilder = new JcaX509v3CertificateBuilder(rootName, serial(),
                Date.from(now.minus(Duration.ofDays(1))), Date.from(now.plus(Duration.ofDays(3650))),
                rootName, rootKeys.getPublic());
        rootBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
        rootBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        rootBuilder.addExtension(Extension.subjectKeyIdentifier, false,
                ext.createSubjectKeyIdentifier(rootKeys.getPublic()));
        X509Certificate root = certificate(rootBuilder.build(contentSigner(rootKeys.getPrivate())));

        KeyPair signerKeys = newKeyPair();
        X509v3CertificateBuilder signerBuilder = new JcaX509v3CertificateBuilder(root, serial(),
                Date.from(now.minus(Duration.ofDays(1))), Date.from(now.plus(Duration.ofDays(730))),
                new X500Name("C=ES,O=SignCheck Demo,CN=SignCheck DEMO Signer - TEST ONLY"), signerKeys.getPublic());
        signerBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        signerBuilder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation));
        signerBuilder.addExtension(Extension.subjectKeyIdentifier, false,
                ext.createSubjectKeyIdentifier(signerKeys.getPublic()));
        signerBuilder.addExtension(Extension.authorityKeyIdentifier, false, ext.createAuthorityKeyIdentifier(root));
        X509Certificate signer = certificate(signerBuilder.build(contentSigner(rootKeys.getPrivate())));

        return new DemoCertificateAuthority(root, rootKeys.getPrivate(), signer, signerKeys.getPrivate());
    }

    private static DemoCertificateAuthority load(Path file) throws IOException, GeneralSecurityException {
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(file)) {
            store.load(in, KEYSTORE_PASSWORD);
        }
        return new DemoCertificateAuthority(
                (X509Certificate) store.getCertificate(ROOT_ALIAS),
                (PrivateKey) store.getKey(ROOT_ALIAS, KEYSTORE_PASSWORD),
                (X509Certificate) store.getCertificate(SIGNER_ALIAS),
                (PrivateKey) store.getKey(SIGNER_ALIAS, KEYSTORE_PASSWORD));
    }

    private void store(Path file) throws IOException, GeneralSecurityException {
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setKeyEntry(ROOT_ALIAS, rootKey, KEYSTORE_PASSWORD, new Certificate[] {root});
        store.setKeyEntry(SIGNER_ALIAS, signerKey, KEYSTORE_PASSWORD, new Certificate[] {signer, root});
        try (OutputStream out = Files.newOutputStream(file)) {
            store.store(out, KEYSTORE_PASSWORD);
        }
    }

    private X509CRL issueCrl() {
        try {
            Instant now = Instant.now();
            X509v2CRLBuilder builder = new X509v2CRLBuilder(
                    X500Name.getInstance(root.getSubjectX500Principal().getEncoded()),
                    Date.from(now.minus(Duration.ofMinutes(5))));
            builder.setNextUpdate(Date.from(now.plus(Duration.ofDays(7))));
            builder.addExtension(Extension.authorityKeyIdentifier, false,
                    new JcaX509ExtensionUtils().createAuthorityKeyIdentifier(root));
            builder.addExtension(Extension.cRLNumber, false, new CRLNumber(BigInteger.valueOf(now.getEpochSecond())));
            return new JcaX509CRLConverter().setProvider(BC).getCRL(builder.build(contentSigner(rootKey)));
        } catch (GeneralSecurityException | OperatorCreationException | IOException e) {
            throw new IllegalStateException("Unable to issue the demo CA CRL", e);
        }
    }

    private static KeyPair newKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
        return generator.generateKeyPair();
    }

    private static BigInteger serial() {
        return new BigInteger(64, new SecureRandom());
    }

    private static ContentSigner contentSigner(PrivateKey key) throws OperatorCreationException {
        return new JcaContentSignerBuilder("SHA256withECDSA").setProvider(BC).build(key);
    }

    private static X509Certificate certificate(org.bouncycastle.cert.X509CertificateHolder holder)
            throws GeneralSecurityException {
        return new JcaX509CertificateConverter().setProvider(BC).getCertificate(holder);
    }
}
