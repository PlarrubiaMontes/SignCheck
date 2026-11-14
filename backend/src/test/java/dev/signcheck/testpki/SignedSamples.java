package dev.signcheck.testpki;

import eu.europa.esig.dss.alert.SilentOnStatusAlert;
import eu.europa.esig.dss.cades.CAdESSignatureParameters;
import eu.europa.esig.dss.cades.signature.CAdESService;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.EncryptionAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignaturePackaging;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.signature.AbstractSignatureParameters;
import eu.europa.esig.dss.signature.DocumentSignatureService;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.spi.signature.AdvancedSignature;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.spi.x509.tsp.KeyEntityTSPSource;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.xades.XAdESSignatureParameters;
import eu.europa.esig.dss.xades.signature.XAdESService;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;

/**
 * Produces signed documents on the fly with DSS and the {@link TestPki}, so no signed file is ever
 * committed to the repository.
 *
 * <h2>How a signature is created</h2>
 * DSS splits creation in three steps, which is also how remote signing (smart cards, HSMs, cloud
 * signing services) works:
 * <ol>
 *   <li>{@code getDataToSign}: DSS prepares the document and returns the bytes to be signed
 *       (the signed attributes or {@code ds:SignedInfo}, which include the hash of the content);</li>
 *   <li>the private key holder signs those bytes (here: plain JCA with the test key);</li>
 *   <li>{@code signDocument}: DSS embeds the signature value and certificates in the document.</li>
 * </ol>
 * Doing step 2 by hand avoids a dependency on a key-store token and makes the flow explicit.
 *
 * <h2>Packaging</h2>
 * <ul>
 *   <li><b>Enveloped</b>: the signature lives inside the signed document (PAdES; XAdES inside the XML).</li>
 *   <li><b>Enveloping</b>: the signed content lives inside the signature (XAdES {@code ds:Object};
 *       CAdES "attached" {@code .p7m}).</li>
 *   <li><b>Detached</b>: signature and content are separate files; the validator needs both.</li>
 * </ul>
 *
 * <p>The {@link CommonCertificateVerifier} used for creation silences DSS's "expired" and
 * "not yet valid" checks: a real signing application must refuse those certificates, but the tests
 * need such signatures to check that the <em>validator</em> catches them.
 */
public final class SignedSamples {

    /** File name under which detached signatures reference their original. */
    public static final String DETACHED_ORIGINAL_NAME = "original.txt";

    /** Arbitrary, TEST-ONLY time-stamping policy identifier required by RFC 3161 responses. */
    public static final String TEST_TSA_POLICY_OID = "1.2.3.4.1";

    private static final DigestAlgorithm DIGEST = DigestAlgorithm.SHA256;

    private SignedSamples() {
    }

    // ---------------------------------------------------------------------------------------- PAdES

    /** PAdES-BASELINE-B signature of a fresh {@link SampleDocuments#pdf()}, claimed signing time = now. */
    public static byte[] padesBaselineB(TestCredential signer) {
        return padesBaselineB(SampleDocuments.pdf(), signer, TestPki.get().now());
    }

    /**
     * PAdES-BASELINE-T: like level B plus a <em>signature timestamp</em> from the test TSA, which proves the
     * signature existed at that time (the "best signature time").
     */
    public static byte[] padesBaselineT(TestCredential signer) {
        PAdESSignatureParameters parameters = new PAdESSignatureParameters();
        parameters.setSignatureLevel(SignatureLevel.PAdES_BASELINE_T);
        PAdESService service = new PAdESService(creationVerifier());
        service.setTspSource(testTsa());
        return sign(service, new InMemoryDocument(SampleDocuments.pdf(), "sample.pdf"), parameters, signer,
                TestPki.get().now());
    }

    /** RFC 3161 time-stamping authority in memory, signing with {@link TestPki#tsa()}. */
    public static KeyEntityTSPSource testTsa() {
        TestCredential tsa = TestPki.get().tsa();
        KeyEntityTSPSource source = new KeyEntityTSPSource(tsa.privateKey(), tsa.certificate(), tsa.chain());
        source.setTsaPolicy(TEST_TSA_POLICY_OID);
        return source;
    }

    /**
     * PAdES-BASELINE-B signature of the given PDF. Signing an already signed PDF adds a second
     * signature in an incremental update, keeping the first one intact.
     *
     * @param pdf         the document to sign
     * @param signer      credential used to sign
     * @param signingTime claimed signing time written in the signature
     */
    public static byte[] padesBaselineB(byte[] pdf, TestCredential signer, Instant signingTime) {
        PAdESSignatureParameters parameters = new PAdESSignatureParameters();
        parameters.setSignatureLevel(SignatureLevel.PAdES_BASELINE_B);
        parameters.setReason("SignCheck TEST signature");
        return sign(new PAdESService(creationVerifier()), new InMemoryDocument(pdf, "sample.pdf"),
                parameters, signer, signingTime);
    }

    // ---------------------------------------------------------------------------------------- XAdES

    /** XAdES-BASELINE-B, the {@code ds:Signature} element inserted into {@link SampleDocuments#xml()}. */
    public static byte[] xadesEnveloped(TestCredential signer) {
        return xades(new InMemoryDocument(SampleDocuments.xml(), "sample.xml"), SignaturePackaging.ENVELOPED,
                signer, false);
    }

    /** XAdES-BASELINE-B whose {@code ds:Object} contains {@link SampleDocuments#xml()} as XML. */
    public static byte[] xadesEnveloping(TestCredential signer) {
        return xades(new InMemoryDocument(SampleDocuments.xml(), "sample.xml"), SignaturePackaging.ENVELOPING,
                signer, true);
    }

    /**
     * XAdES-BASELINE-B detached signature of {@link SampleDocuments#text()}. The signature references the
     * original by file name ({@value #DETACHED_ORIGINAL_NAME}).
     */
    public static byte[] xadesDetached(TestCredential signer) {
        return xades(new InMemoryDocument(SampleDocuments.text(), DETACHED_ORIGINAL_NAME), SignaturePackaging.DETACHED,
                signer, false);
    }

    // ---------------------------------------------------------------------------------------- CAdES

    /** CAdES-BASELINE-B "attached" (enveloping, {@code .p7m}) signature of {@link SampleDocuments#text()}. */
    public static byte[] cadesAttached(TestCredential signer) {
        return cades(SignaturePackaging.ENVELOPING, signer);
    }

    /** CAdES-BASELINE-B detached ({@code .p7s}) signature of {@link SampleDocuments#text()}. */
    public static byte[] cadesDetached(TestCredential signer) {
        return cades(SignaturePackaging.DETACHED, signer);
    }

    // ---------------------------------------------------------------------------------------- broken copies

    /**
     * Returns a copy of a signed document in which one byte of the signed content was changed
     * (the first letter of {@link SampleDocuments#CONTENT_MARKER}). The file structure stays intact,
     * so the validator can still parse it and must report the hash mismatch.
     */
    public static byte[] tamper(byte[] signed) {
        byte[] marker = SampleDocuments.CONTENT_MARKER.getBytes(StandardCharsets.US_ASCII);
        int index = indexOf(signed, marker);
        if (index < 0) {
            throw new IllegalArgumentException("Signed content marker not found; cannot tamper the document");
        }
        byte[] copy = Arrays.copyOf(signed, signed.length);
        copy[index] = (byte) (copy[index] == 'X' ? 'Y' : 'X');
        return copy;
    }

    /**
     * Returns a copy of a signed PDF in which the signature value of one signature was altered (one hex
     * digit inside its {@code /Contents}). The signed content is untouched, so the hash still matches but
     * the cryptographic verification of that one signature fails; other signatures are unaffected.
     *
     * @param pdf            signed PDF
     * @param signatureIndex index of the signature in DSS's order (0 = first)
     */
    public static byte[] corruptSignatureValue(byte[] pdf, int signatureIndex) {
        List<AdvancedSignature> signatures =
                SignedDocumentValidator.fromDocument(new InMemoryDocument(pdf)).getSignatures();
        byte[] value = signatures.get(signatureIndex).getSignatureValue();
        for (String hex : List.of(HexFormat.of().formatHex(value), HexFormat.of().withUpperCase().formatHex(value))) {
            int index = indexOf(pdf, hex.getBytes(StandardCharsets.US_ASCII));
            if (index >= 0) {
                byte[] copy = Arrays.copyOf(pdf, pdf.length);
                int target = index + hex.length() / 2;
                copy[target] = (byte) (copy[target] == '0' ? '1' : '0');
                return copy;
            }
        }
        throw new IllegalArgumentException("Signature value not found in the PDF");
    }

    // ---------------------------------------------------------------------------------------- internals

    private static byte[] xades(DSSDocument document, SignaturePackaging packaging, TestCredential signer,
            boolean embedXml) {
        XAdESSignatureParameters parameters = new XAdESSignatureParameters();
        parameters.setSignatureLevel(SignatureLevel.XAdES_BASELINE_B);
        parameters.setSignaturePackaging(packaging);
        parameters.setEmbedXML(embedXml);
        return sign(new XAdESService(creationVerifier()), document, parameters, signer, TestPki.get().now());
    }

    private static byte[] cades(SignaturePackaging packaging, TestCredential signer) {
        CAdESSignatureParameters parameters = new CAdESSignatureParameters();
        parameters.setSignatureLevel(SignatureLevel.CAdES_BASELINE_B);
        parameters.setSignaturePackaging(packaging);
        return sign(new CAdESService(creationVerifier()),
                new InMemoryDocument(SampleDocuments.text(), DETACHED_ORIGINAL_NAME), parameters, signer,
                TestPki.get().now());
    }

    /** The three-step creation flow, common to every format. */
    static <P extends AbstractSignatureParameters<?>> byte[] sign(
            DocumentSignatureService<P, ?> service, DSSDocument document, P parameters, TestCredential signer,
            Instant signingTime) {
        parameters.setDigestAlgorithm(DIGEST);
        parameters.setSigningCertificate(new CertificateToken(signer.certificate()));
        parameters.setCertificateChain(signer.chain().stream().map(CertificateToken::new).toList());
        parameters.bLevel().setSigningDate(Date.from(signingTime));

        ToBeSigned dataToSign = service.getDataToSign(document, parameters);
        SignatureValue signatureValue = sign(dataToSign, signer);
        return DSSUtils.toByteArray(service.signDocument(document, parameters, signatureValue));
    }

    /** Step 2 of the signing flow: sign DSS's data-to-be-signed with the test private key. */
    static SignatureValue sign(ToBeSigned dataToSign, TestCredential signer) {
        SignatureAlgorithm algorithm = SignatureAlgorithm.getAlgorithm(
                EncryptionAlgorithm.forKey(signer.certificate().getPublicKey()), DIGEST);
        try {
            Signature signature = Signature.getInstance(algorithm.getJCEId());
            signature.initSign(signer.privateKey());
            signature.update(dataToSign.getBytes());
            return new SignatureValue(algorithm, signature.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to sign with the test key", e);
        }
    }

    static CommonCertificateVerifier creationVerifier() {
        CommonCertificateVerifier verifier = new CommonCertificateVerifier();
        verifier.setAlertOnExpiredCertificate(new SilentOnStatusAlert());
        verifier.setAlertOnNotYetValidCertificate(new SilentOnStatusAlert());
        return verifier;
    }

    private static int indexOf(byte[] data, byte[] pattern) {
        outer:
        for (int i = 0; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
