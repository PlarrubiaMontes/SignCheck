package dev.signcheck.testpki;

import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.xades.XAdESSignatureParameters;
import eu.europa.esig.dss.xades.signature.XAdESService;
import eu.europa.esig.dss.xades.tsl.AbstractTrustedListSignatureParametersBuilder;
import eu.europa.esig.dss.xades.tsl.TrustedListV5SignatureParametersBuilder;
import eu.europa.esig.dss.xades.tsl.TrustedListV6SignatureParametersBuilder;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Generates a signed test <em>List of Trusted Lists</em> (LOTL) and a national <em>Trusted List</em> (TL)
 * following ETSI TS 119 612, so the LOTL trust mode can be tested offline end to end.
 *
 * <pre>
 * LOTL ({@link #LOTL_URL}), signed by {@link TestPki#lotlSigner()}, announced in {@link #OFFICIAL_JOURNAL_URL}
 * └── pointer to TL "ZZ" ({@link #TL_URL}), whose allowed signer is {@link TestPki#tlSigner()}
 *     └── TSP "SignCheck TEST TSP"
 *         └── service CA/QC, status granted, digital identity = {@link TestPki#root()}
 * </pre>
 *
 * Each list is validated against the official XSD of its version before signing (DSS's
 * {@code assertConfigurationIsValid}), so a structural mistake here fails fast instead of producing
 * a list DSS would silently ignore. All URLs use the reserved {@code .test} domain.
 */
public final class TestTrustedLists {

    public static final String LOTL_URL = "http://lotl.signcheck.test/eu-lotl.xml";
    public static final String TL_URL = "http://tl.signcheck.test/zz-tl.xml";
    /** Plays the Official Journal: DSS only checks that the LOTL's SchemeInformationURI shares its host. */
    public static final String OFFICIAL_JOURNAL_URL = "http://oj.signcheck.test/announcement-of-lotl-signers";

    private static final String NS = "http://uri.etsi.org/02231/v2#";
    private static final String TSL_TAG = "http://uri.etsi.org/19612/TSLTag";

    private TestTrustedLists() {
    }

    /** LOTL (version 6) pointing to the test TL and signed by {@code signer}. */
    public static byte[] lotl(TestCredential signer) {
        TestPki pki = TestPki.get();
        String pointer = """
                <tsl:PointersToOtherTSL>
                  <tsl:OtherTSLPointer>
                    <tsl:ServiceDigitalIdentities>
                      <tsl:ServiceDigitalIdentity>
                        <tsl:DigitalId><tsl:X509Certificate>%s</tsl:X509Certificate></tsl:DigitalId>
                      </tsl:ServiceDigitalIdentity>
                    </tsl:ServiceDigitalIdentities>
                    <tsl:TSLLocation>%s</tsl:TSLLocation>
                    <tsl:AdditionalInformation>
                      <tsl:OtherInformation><tsl:TSLType>http://uri.etsi.org/TrstSvc/TrustedList/TSLType/EUgeneric</tsl:TSLType></tsl:OtherInformation>
                      <tsl:OtherInformation><tsl:SchemeOperatorName><tsl:Name xml:lang="en">SignCheck TEST TL operator</tsl:Name></tsl:SchemeOperatorName></tsl:OtherInformation>
                      <tsl:OtherInformation><tsl:SchemeTypeCommunityRules><tsl:URI xml:lang="en">http://uri.etsi.org/TrstSvc/TrustedList/schemerules/EUcommon</tsl:URI></tsl:SchemeTypeCommunityRules></tsl:OtherInformation>
                      <tsl:OtherInformation><tsl:SchemeTerritory>ZZ</tsl:SchemeTerritory></tsl:OtherInformation>
                      <tsl:OtherInformation><tslx:MimeType xmlns:tslx="http://uri.etsi.org/02231/v2/additionaltypes#">application/vnd.etsi.tsl+xml</tslx:MimeType></tsl:OtherInformation>
                    </tsl:AdditionalInformation>
                  </tsl:OtherTSLPointer>
                </tsl:PointersToOtherTSL>
                """.formatted(base64(pki.tlSigner().certificate()), TL_URL);
        String xml = trustStatusList(6, "lotl",
                "http://uri.etsi.org/TrstSvc/TrustedList/TSLType/EUlistofthelists",
                "SignCheck TEST LOTL operator", OFFICIAL_JOURNAL_URL,
                "http://uri.etsi.org/TrstSvc/TrustedList/StatusDetn/EUlistofthelists",
                "http://uri.etsi.org/TrstSvc/TrustedList/schemerules/EUlistofthelists", "EU",
                pointer, "");
        return sign(xml, 6, signer);
    }

    /**
     * National TL whose only service is the test root CA.
     *
     * @param version Trusted List version (5 or 6)
     * @param signer  credential signing the list
     */
    public static byte[] tl(int version, TestCredential signer) {
        TestPki pki = TestPki.get();
        String providers = """
                <tsl:TrustServiceProviderList>
                  <tsl:TrustServiceProvider>
                    <tsl:TSPInformation>
                      <tsl:TSPName><tsl:Name xml:lang="en">SignCheck TEST TSP</tsl:Name></tsl:TSPName>
                      %s
                      <tsl:TSPInformationURI><tsl:URI xml:lang="en">http://tsp.signcheck.test/</tsl:URI></tsl:TSPInformationURI>
                    </tsl:TSPInformation>
                    <tsl:TSPServices>
                      <tsl:TSPService>
                        <tsl:ServiceInformation>
                          <tsl:ServiceTypeIdentifier>http://uri.etsi.org/TrstSvc/Svctype/CA/QC</tsl:ServiceTypeIdentifier>
                          <tsl:ServiceName><tsl:Name xml:lang="en">SignCheck TEST Root CA</tsl:Name></tsl:ServiceName>
                          <tsl:ServiceDigitalIdentity>
                            <tsl:DigitalId><tsl:X509Certificate>%s</tsl:X509Certificate></tsl:DigitalId>
                          </tsl:ServiceDigitalIdentity>
                          <tsl:ServiceStatus>http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/granted</tsl:ServiceStatus>
                          <tsl:StatusStartingTime>%s</tsl:StatusStartingTime>
                        </tsl:ServiceInformation>
                      </tsl:TSPService>
                    </tsl:TSPServices>
                  </tsl:TrustServiceProvider>
                </tsl:TrustServiceProviderList>
                """.formatted(address(), base64(pki.root().certificate()),
                pki.now().minus(Duration.ofDays(365)));
        String xml = trustStatusList(version, "tl-zz",
                "http://uri.etsi.org/TrstSvc/TrustedList/TSLType/EUgeneric",
                "SignCheck TEST TL operator", "http://tl.signcheck.test/scheme",
                "http://uri.etsi.org/TrstSvc/TrustedList/StatusDetn/EUappropriate",
                "http://uri.etsi.org/TrstSvc/TrustedList/schemerules/EUcommon", "ZZ",
                "", providers);
        return sign(xml, version, signer);
    }

    private static String trustStatusList(int version, String id, String type, String operator, String schemeUri,
            String statusDetermination, String communityRules, String territory, String pointers, String providers) {
        Instant now = TestPki.get().now();
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <tsl:TrustServiceStatusList xmlns:tsl="%s" xmlns:ds="http://www.w3.org/2000/09/xmldsig#" Id="%s" TSLTag="%s">
                  <tsl:SchemeInformation>
                    <tsl:TSLVersionIdentifier>%d</tsl:TSLVersionIdentifier>
                    <tsl:TSLSequenceNumber>1</tsl:TSLSequenceNumber>
                    <tsl:TSLType>%s</tsl:TSLType>
                    <tsl:SchemeOperatorName><tsl:Name xml:lang="en">%s</tsl:Name></tsl:SchemeOperatorName>
                    <tsl:SchemeOperatorAddress>%s</tsl:SchemeOperatorAddress>
                    <tsl:SchemeName><tsl:Name xml:lang="en">SignCheck TEST scheme, not a real Trusted List</tsl:Name></tsl:SchemeName>
                    <tsl:SchemeInformationURI><tsl:URI xml:lang="en">%s</tsl:URI></tsl:SchemeInformationURI>
                    <tsl:StatusDeterminationApproach>%s</tsl:StatusDeterminationApproach>
                    <tsl:SchemeTypeCommunityRules><tsl:URI xml:lang="en">%s</tsl:URI></tsl:SchemeTypeCommunityRules>
                    <tsl:SchemeTerritory>%s</tsl:SchemeTerritory>
                    <tsl:PolicyOrLegalNotice><tsl:TSLLegalNotice xml:lang="en">TEST ONLY</tsl:TSLLegalNotice></tsl:PolicyOrLegalNotice>
                    <tsl:HistoricalInformationPeriod>65535</tsl:HistoricalInformationPeriod>
                    %s
                    <tsl:ListIssueDateTime>%s</tsl:ListIssueDateTime>
                    <tsl:NextUpdate><tsl:dateTime>%s</tsl:dateTime></tsl:NextUpdate>
                  </tsl:SchemeInformation>
                  %s
                </tsl:TrustServiceStatusList>
                """.formatted(NS, id, TSL_TAG, version, type, operator,
                        innerAddress(), schemeUri, statusDetermination, communityRules, territory, pointers,
                        now.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(180)), providers);
    }

    private static String address() {
        return "<tsl:TSPAddress>" + innerAddress() + "</tsl:TSPAddress>";
    }

    private static String innerAddress() {
        return """
                <tsl:PostalAddresses>
                  <tsl:PostalAddress xml:lang="en">
                    <tsl:StreetAddress>1 Test Street</tsl:StreetAddress>
                    <tsl:Locality>Testville</tsl:Locality>
                    <tsl:CountryName>ZZ</tsl:CountryName>
                  </tsl:PostalAddress>
                </tsl:PostalAddresses>
                <tsl:ElectronicAddress><tsl:URI xml:lang="en">mailto:nobody@signcheck.test</tsl:URI></tsl:ElectronicAddress>
                """;
    }

    private static byte[] sign(String xml, int version, TestCredential signer) {
        DSSDocument unsigned = new InMemoryDocument(xml.getBytes(StandardCharsets.UTF_8), "tl.xml");
        CertificateToken signingCertificate = new CertificateToken(signer.certificate());
        AbstractTrustedListSignatureParametersBuilder builder = version == 5
                ? new TrustedListV5SignatureParametersBuilder(signingCertificate, unsigned)
                : new TrustedListV6SignatureParametersBuilder(signingCertificate, unsigned);
        builder.assertConfigurationIsValid();
        XAdESSignatureParameters parameters = builder.build();
        return SignedSamples.sign(new XAdESService(SignedSamples.creationVerifier()), unsigned, parameters, signer,
                TestPki.get().now());
    }

    private static String base64(X509Certificate certificate) {
        try {
            return Base64.getEncoder().encodeToString(certificate.getEncoded());
        } catch (CertificateEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
