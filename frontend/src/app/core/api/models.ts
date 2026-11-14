/**
 * Mirrors of the backend DTOs (dev.signcheck.validation.dto and dev.signcheck.trust.dto).
 * Keep in sync with docs/SPEC.md §4.
 */

export type Verdict = 'VALID' | 'INDETERMINATE' | 'INVALID';

export type TrustMode = 'NONE' | 'KEYSTORE' | 'LOTL';

export interface SignerInfo {
  commonName: string | null;
  organization: string | null;
  country: string | null;
  certificateSerialNumber: string | null;
}

export interface CertificateInfo {
  subject: string;
  issuer: string;
  notBefore: string;
  notAfter: string;
  trusted: boolean;
}

export interface RevocationInfo {
  status: 'GOOD' | 'REVOKED' | 'UNKNOWN' | null;
  source: 'CRL' | 'OCSP' | null;
  producedAt: string | null;
}

export interface TimestampInfo {
  type: string | null;
  productionTime: string | null;
  tsaName: string | null;
  valid: boolean;
}

export interface SignatureResult {
  id: string;
  verdict: Verdict;
  subIndication: string | null;
  reason: string | null;
  format: string | null;
  level: string | null;
  qualification: string | null;
  signer: SignerInfo | null;
  issuer: string | null;
  claimedSigningTime: string | null;
  bestSignatureTime: string | null;
  certificateChain: CertificateInfo[];
  revocation: RevocationInfo | null;
  timestamps: TimestampInfo[];
  warnings: string[];
  errors: string[];
}

export interface ValidationSummary {
  signatureCount: number;
  validCount: number;
  overallVerdict: Verdict;
}

export interface RawReports {
  simpleReport: string;
  detailedReport: string;
  diagnosticData: string;
  etsiValidationReport: string;
}

export interface ValidationResponse {
  fileName: string | null;
  detectedFormat: string | null;
  validationTime: string;
  trustMode: TrustMode;
  summary: ValidationSummary;
  signatures: SignatureResult[];
  rawReports: RawReports | null;
}

export interface TrustStatus {
  mode: TrustMode;
  loading: boolean;
  lastSuccessfulRefresh: string | null;
  trustedListCount: number;
  trustedCertificateCount: number;
}

/** Stable error codes of the backend's RFC 9457 ProblemDetail responses. */
export type ErrorCode =
  | 'NO_FILE'
  | 'FILE_TOO_LARGE'
  | 'NO_SIGNATURE_FOUND'
  | 'UNSUPPORTED_FORMAT'
  | 'VALIDATION_ERROR'
  | 'NETWORK_ERROR';

export interface ApiProblem {
  status: number;
  code: ErrorCode | string;
  detail: string | null;
}
