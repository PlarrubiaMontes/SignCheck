package dev.signcheck.validation;

import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import org.springframework.stereotype.Component;

/**
 * Chooses the DSS validator for a document by looking at its <em>content</em>.
 *
 * <p>Each DSS format module (PAdES, XAdES, CAdES, ASiC) registers a factory through
 * {@link java.util.ServiceLoader}; {@link SignedDocumentValidator#fromDocument(DSSDocument)} asks each
 * one whether it recognises the bytes (PDF header, XML with a {@code ds:Signature}, ASN.1 CMS
 * structure...). The file extension is never trusted. Wrapping this static call keeps the DSS
 * exception out of the service and gives tests a seam.
 */
@Component
public class DocumentValidatorFactory {

    /**
     * @throws UnsupportedFormatException if no DSS module recognises the document
     */
    public SignedDocumentValidator create(DSSDocument document) {
        try {
            return SignedDocumentValidator.fromDocument(document);
        } catch (UnsupportedOperationException e) {
            throw new UnsupportedFormatException(e);
        }
    }
}
