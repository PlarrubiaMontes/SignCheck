import { SignatureResult, ValidationResponse } from '../core/api/models';

/** Builders of API payloads for tests. */
export function signature(overrides: Partial<SignatureResult> = {}): SignatureResult {
  return {
    id: 'S-1',
    verdict: 'VALID',
    subIndication: null,
    reason: null,
    format: 'PAdES',
    level: 'PAdES-BASELINE-B',
    qualification: 'N/A',
    signer: { commonName: 'TEST SIGNER Valid', organization: 'SignCheck Test PKI', country: 'ES', certificateSerialNumber: '3EB' },
    issuer: 'CN=SignCheck TEST Intermediate CA,O=SignCheck Test PKI,C=ES',
    claimedSigningTime: '2026-11-14T10:00:00Z',
    bestSignatureTime: '2026-11-14T10:05:00Z',
    certificateChain: [
      { subject: 'CN=TEST SIGNER Valid', issuer: 'CN=SignCheck TEST Intermediate CA', notBefore: '2026-01-01T00:00:00Z', notAfter: '2027-01-01T00:00:00Z', trusted: false },
      { subject: 'CN=SignCheck TEST Root CA', issuer: 'CN=SignCheck TEST Root CA', notBefore: '2020-01-01T00:00:00Z', notAfter: '2036-01-01T00:00:00Z', trusted: true },
    ],
    revocation: { status: 'GOOD', source: 'CRL', producedAt: '2026-11-14T09:00:00Z' },
    timestamps: [],
    warnings: [],
    errors: [],
    ...overrides,
  };
}

export function validationResponse(overrides: Partial<ValidationResponse> = {}): ValidationResponse {
  return {
    fileName: 'contract.pdf',
    detectedFormat: 'PAdES',
    validationTime: '2026-11-14T10:05:00Z',
    trustMode: 'KEYSTORE',
    summary: { signatureCount: 1, validCount: 1, overallVerdict: 'VALID' },
    signatures: [signature()],
    rawReports: null,
    ...overrides,
  };
}
