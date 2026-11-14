package dev.signcheck.validation.dto;

/**
 * Identity of the signer, taken from the subject of the signing certificate. Only these fields are
 * exposed (no e-mail, address or personal ID numbers).
 *
 * @param commonName              subject CN
 * @param organization            subject O, if present
 * @param country                 subject C, if present
 * @param certificateSerialNumber serial number of the signing certificate, hexadecimal, upper case
 */
public record SignerInfo(String commonName, String organization, String country, String certificateSerialNumber) {
}
