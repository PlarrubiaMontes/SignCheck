package dev.signcheck.signing;

import dev.signcheck.signing.SigningOptions.Format;
import dev.signcheck.signing.SigningOptions.Level;
import dev.signcheck.signing.SigningOptions.Packaging;
import eu.europa.esig.dss.cades.CAdESSignatureParameters;
import eu.europa.esig.dss.cades.signature.CAdESService;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.EncryptionAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignaturePackaging;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.DSSException;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.signature.AbstractSignatureParameters;
import eu.europa.esig.dss.signature.AbstractSignatureService;
import eu.europa.esig.dss.signature.DocumentSignatureService;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.spi.exception.IllegalInputException;
import eu.europa.esig.dss.spi.validation.CertificateVerifier;
import eu.europa.esig.dss.spi.x509.tsp.TSPSource;
import eu.europa.esig.dss.xades.XAdESSignatureParameters;
import eu.europa.esig.dss.xades.signature.XAdESService;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.util.Date;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Signs documents with the {@link DemoCertificateAuthority}'s TEST-ONLY signer.
 *
 * <h2>How a signature is created</h2>
 * DSS splits creation in three steps, the same ones a smart card, an HSM or a remote signing service uses:
 * <ol>
 *   <li>{@code getDataToSign}: DSS prepares the signature structure and returns the bytes to sign
 *       (the signed attributes / {@code ds:SignedInfo}, which contain the hash of the document);</li>
 *   <li>the private key signs those bytes; only this step needs the key;</li>
 *   <li>{@code signDocument}: DSS assembles the final document. For level T it first asks a time-stamping
 *       authority (RFC 3161) to timestamp the signature value.</li>
 * </ol>
 *
 * <p>Allowed combinations: PAdES enveloped (PDF input); XAdES enveloped (XML input), enveloping or detached;
 * CAdES enveloping ({@code .p7m}) or detached ({@code .p7s}). Level T needs {@code signcheck.signing.tsa-url}.
 * The document is processed in memory and never stored; only metadata is logged.
 */
@Service
@ConditionalOnProperty(prefix = "signcheck.signing", name = "enabled", havingValue = "true")
public class SigningService {

    private static final Logger LOG = LoggerFactory.getLogger(SigningService.class);
    private static final DigestAlgorithm DIGEST = DigestAlgorithm.SHA256;

    private final DemoCertificateAuthority demoCa;
    private final CertificateVerifier certificateVerifier;
    private final ObjectProvider<TSPSource> tspSource;

    public SigningService(DemoCertificateAuthority demoCa, CertificateVerifier certificateVerifier,
            ObjectProvider<TSPSource> tspSource) {
        this.demoCa = demoCa;
        this.certificateVerifier = certificateVerifier;
        this.tspSource = tspSource;
    }

    /** Whether level T can be produced (a time-stamping authority is configured). */
    public boolean timestampingAvailable() {
        return tspSource.getIfAvailable() != null;
    }

    /**
     * @param fileName  original file name (used to name the result)
     * @param content   document to sign
     * @param format    signature format
     * @param packaging packaging, or {@code null} for the format's default
     * @param level     baseline level
     * @throws InvalidSigningRequestException for unsupported combinations or unsuitable files
     */
    public SignedFile sign(String fileName, byte[] content, Format format, Packaging packaging, Level level) {
        Packaging effective = packaging == null ? defaultPackaging(format) : packaging;
        checkCombination(format, effective, level);
        TSPSource tsp = level == Level.T ? tspSource.getIfAvailable() : null;
        if (level == Level.T && tsp == null) {
            throw new InvalidSigningRequestException("Level T needs a time-stamping authority, none is configured");
        }
        DSSDocument document = new InMemoryDocument(content, fileName);
        try {
            byte[] signed = switch (format) {
                case PADES -> sign(withTsp(new PAdESService(certificateVerifier), tsp), document,
                        pades(level));
                case XADES -> sign(withTsp(new XAdESService(certificateVerifier), tsp), document,
                        xades(effective, level));
                case CADES -> sign(withTsp(new CAdESService(certificateVerifier), tsp), document,
                        cades(effective, level));
            };
            LOG.info("Signed document: size={} bytes, format={}, packaging={}, level={}",
                    content.length, format, effective, level);
            return new SignedFile(resultName(fileName, format, effective), mimeType(format, effective), signed);
        } catch (DSSException | IllegalInputException | IllegalArgumentException | UnsupportedOperationException e) {
            throw new InvalidSigningRequestException(
                    "The file cannot be signed as " + format + " " + effective + " (wrong file type?)", e);
        }
    }

    // ------------------------------------------------------------------------------------------

    static Packaging defaultPackaging(Format format) {
        return switch (format) {
            case PADES, XADES -> Packaging.ENVELOPED;
            case CADES -> Packaging.ENVELOPING;
        };
    }

    static void checkCombination(Format format, Packaging packaging, Level level) {
        boolean supported = switch (format) {
            case PADES -> packaging == Packaging.ENVELOPED;
            case XADES -> true;
            case CADES -> packaging != Packaging.ENVELOPED;
        };
        if (!supported) {
            throw new InvalidSigningRequestException(format + " does not support " + packaging + " packaging");
        }
    }

    private static <S extends AbstractSignatureService<?, ?>> S withTsp(S service, TSPSource tsp) {
        if (tsp != null) {
            service.setTspSource(tsp);
        }
        return service;
    }

    private static PAdESSignatureParameters pades(Level level) {
        PAdESSignatureParameters parameters = new PAdESSignatureParameters();
        parameters.setSignatureLevel(level == Level.T ? SignatureLevel.PAdES_BASELINE_T : SignatureLevel.PAdES_BASELINE_B);
        parameters.setReason("SignCheck DEMO signature - TEST ONLY");
        return parameters;
    }

    private static XAdESSignatureParameters xades(Packaging packaging, Level level) {
        XAdESSignatureParameters parameters = new XAdESSignatureParameters();
        parameters.setSignatureLevel(level == Level.T ? SignatureLevel.XAdES_BASELINE_T : SignatureLevel.XAdES_BASELINE_B);
        parameters.setSignaturePackaging(toDss(packaging));
        return parameters;
    }

    private static CAdESSignatureParameters cades(Packaging packaging, Level level) {
        CAdESSignatureParameters parameters = new CAdESSignatureParameters();
        parameters.setSignatureLevel(level == Level.T ? SignatureLevel.CAdES_BASELINE_T : SignatureLevel.CAdES_BASELINE_B);
        parameters.setSignaturePackaging(toDss(packaging));
        return parameters;
    }

    private static SignaturePackaging toDss(Packaging packaging) {
        return SignaturePackaging.valueOf(packaging.name());
    }

    /** The three-step flow, common to every format. */
    private <P extends AbstractSignatureParameters<?>> byte[] sign(
            DocumentSignatureService<P, ?> service, DSSDocument document, P parameters) {
        parameters.setDigestAlgorithm(DIGEST);
        parameters.setSigningCertificate(new CertificateToken(demoCa.signer()));
        parameters.setCertificateChain(demoCa.signerChain().stream().map(CertificateToken::new).toList());
        parameters.bLevel().setSigningDate(new Date());

        ToBeSigned dataToSign = service.getDataToSign(document, parameters);
        SignatureValue value = signWithDemoKey(dataToSign);
        return DSSUtils.toByteArray(service.signDocument(document, parameters, value));
    }

    private SignatureValue signWithDemoKey(ToBeSigned dataToSign) {
        SignatureAlgorithm algorithm = SignatureAlgorithm.getAlgorithm(
                EncryptionAlgorithm.forKey(demoCa.signer().getPublicKey()), DIGEST);
        try {
            Signature signature = Signature.getInstance(algorithm.getJCEId());
            signature.initSign(demoCa.signerKey());
            signature.update(dataToSign.getBytes());
            return new SignatureValue(algorithm, signature.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to sign with the demo key", e);
        }
    }

    static String resultName(String fileName, Format format, Packaging packaging) {
        String name = fileName == null || fileName.isBlank() ? "document" : fileName;
        String base = name.contains(".") ? name.substring(0, name.lastIndexOf('.')) : name;
        return switch (format) {
            case PADES -> base + "-signed.pdf";
            case XADES -> packaging == Packaging.DETACHED ? name + ".xsig" : base + "-signed.xml";
            case CADES -> name + (packaging == Packaging.DETACHED ? ".p7s" : ".p7m");
        };
    }

    static String mimeType(Format format, Packaging packaging) {
        return switch (format) {
            case PADES -> "application/pdf";
            case XADES -> "application/xml";
            case CADES -> packaging == Packaging.DETACHED ? "application/pkcs7-signature" : "application/pkcs7-mime";
        };
    }

    /**
     * @param name     suggested file name of the result
     * @param mimeType media type of the result
     * @param content  signed document (or detached signature)
     */
    public record SignedFile(String name, String mimeType, byte[] content) {
    }
}
