package dev.signcheck.signing;

import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.model.x509.revocation.crl.CRL;
import eu.europa.esig.dss.spi.x509.revocation.RevocationSource;
import eu.europa.esig.dss.spi.x509.revocation.RevocationToken;
import eu.europa.esig.dss.spi.x509.revocation.crl.ExternalResourcesCRLSource;
import java.security.cert.CRLException;

/**
 * Revocation information for certificates issued by the {@link DemoCertificateAuthority}: its current CRL,
 * answered locally because the application itself is the CA. Certificates of any other issuer get no answer
 * here, so the next (online) source is asked.
 */
public class DemoCaRevocationSource implements RevocationSource<CRL> {

    private final transient DemoCertificateAuthority demoCa;
    private final CertificateToken demoRoot;

    public DemoCaRevocationSource(DemoCertificateAuthority demoCa) {
        this.demoCa = demoCa;
        this.demoRoot = new CertificateToken(demoCa.root());
    }

    @Override
    public RevocationToken<CRL> getRevocationToken(CertificateToken certificate, CertificateToken issuer) {
        if (issuer == null || !issuer.equals(demoRoot)) {
            return null;
        }
        try {
            return new ExternalResourcesCRLSource(new InMemoryDocument(demoCa.crl().getEncoded()))
                    .getRevocationToken(certificate, issuer);
        } catch (CRLException e) {
            throw new IllegalStateException("Unable to encode the demo CA CRL", e);
        }
    }
}
