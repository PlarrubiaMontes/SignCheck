package dev.signcheck.signing;

/** Types of the signing API (public enums of the REST contract, independent from DSS's own enums). */
public final class SigningOptions {

    private SigningOptions() {
    }

    /** Signature format: where and how the signature is encoded. */
    public enum Format {
        /** PDF Advanced Electronic Signature, inside the PDF (ETSI EN 319 142). */
        PADES,
        /** XML Advanced Electronic Signature (ETSI EN 319 132). */
        XADES,
        /** CMS Advanced Electronic Signature (ETSI EN 319 122), any file type. */
        CADES
    }

    /** Where the signature lives relative to the signed data. */
    public enum Packaging {
        /** Signature inside the signed document (PAdES; XAdES inside an XML document). */
        ENVELOPED,
        /** Signed data inside the signature (XAdES ds:Object; CAdES .p7m). */
        ENVELOPING,
        /** Separate signature file; the original is needed to validate it (XAdES; CAdES .p7s). */
        DETACHED
    }

    /** ETSI baseline level. */
    public enum Level {
        /** Basic signature. */
        B,
        /** B plus a timestamp from a time-stamping authority proving when the signature existed. */
        T
    }
}
