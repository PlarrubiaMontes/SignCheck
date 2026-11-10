# SignCheck — Functional specification

## 1. Goal

Let anyone upload an electronically signed file and get a clear, trustworthy answer to:
**"Is this signature valid? Who signed it, when, and with what level of assurance?"**,
plus the full technical evidence for experts.

Target users:

- Non-experts who receive a signed document and want a clear verdict.
- Developers and integrators who need the technical details (signature level, certificate chain,
  revocation, timestamps, raw ETSI reports) or want to call a REST API.

## 2. Supported formats

| Format | Typical input | Packaging | Notes |
|---|---|---|---|
| PAdES | `.pdf` | embedded in the PDF | may contain several signatures and document timestamps |
| XAdES | `.xml`, `.xsig` | enveloped, enveloping, detached | detached requires the original file(s) |
| CAdES | `.p7s`, `.p7m`, `.csig` | attached (enveloping), detached | detached requires the original file |
| ASiC-S / ASiC-E (optional) | `.asics`, `.asice`, `.scs`, `.sce` | ZIP container with XAdES or CAdES | phase 2 of validation |

Signature levels to report: **B, T, LT, LTA** (ETSI baseline profiles), or "not baseline" if it doesn't match.

The format is **detected automatically** by DSS from the file content, never from the extension alone.

## 3. Functional requirements

### Validation (MVP)

- **FR-1** Upload one signed file (max 20 MB, configurable) and validate all signatures it contains.
- **FR-2** Optionally upload one or more original documents for detached signatures.
  If a detached signature is uploaded without originals, return a clear `INDETERMINATE`
  result explaining that the signed data is missing.
- **FR-3** For each signature, report:
  - verdict: `VALID`, `INDETERMINATE` or `INVALID`
  - human-readable reason when not valid (from DSS sub-indication, translated)
  - format, packaging and level (e.g. `PAdES-BASELINE-LT`)
  - signer: common name, organisation, country, serial number of certificate (no other personal data)
  - issuer of the signing certificate
  - claimed signing time and, if present, best signature time proven by timestamps
  - whether the certificate is qualified (QES / AdES-QC / AdES) when the trust source allows it
  - certificate chain (subject, issuer, validity dates, trusted or not)
  - revocation status of the signing certificate (good / revoked / unknown, and source OCSP or CRL)
  - timestamps (type, production time, TSA name, valid or not)
  - warnings and errors from the validation
- **FR-4** Summary for the whole document: number of signatures, number valid, overall verdict
  (valid only if all signatures are valid).
- **FR-5** Optionally return the raw DSS reports (simple, detailed, diagnostic data, ETSI validation report)
  as XML strings so the user can download them.
- **FR-6** Expose the status of the trust anchors: mode in use, last successful LOTL refresh,
  number of trusted lists loaded, whether loading is still in progress.

### Signing (phase 2)

- **FR-7** Sign an uploaded file as PAdES, XAdES or CAdES using a **demo certificate** generated
  by the application's own test CA (never user-supplied keys in the public demo).
- **FR-8** Choose packaging (when applicable) and level B or T (T uses a configurable public TSA).
- **FR-9** Download the signed file. The signed file can be validated straight away with SignCheck
  (trust mode must include the demo CA for it to be `VALID`).

### Extras (later)

- **FR-10** Batch validation of several files in one request.
- **FR-11** UI in English and Spanish.
- **FR-12** Downloadable human-friendly PDF report.

## 4. REST API (v1)

Base path: `/api/v1`. JSON responses. OpenAPI available at `/swagger-ui.html` and `/v3/api-docs`.

### `POST /api/v1/validations`

`multipart/form-data`:

| Part | Required | Description |
|---|---|---|
| `file` | yes | the signed file |
| `originals` | no | one or more original documents for detached signatures |
| `includeRawReports` | no | `true` to include raw DSS XML reports (default `false`) |

Response `200 OK`:

```json
{
  "fileName": "contract-signed.pdf",
  "detectedFormat": "PAdES",
  "validationTime": "2026-11-10T18:00:00Z",
  "trustMode": "LOTL",
  "summary": {
    "signatureCount": 2,
    "validCount": 1,
    "overallVerdict": "INDETERMINATE"
  },
  "signatures": [
    {
      "id": "S-8A3F...",
      "verdict": "VALID",
      "reason": null,
      "format": "PAdES",
      "level": "PAdES-BASELINE-LT",
      "qualification": "QESig",
      "signer": {
        "commonName": "TEST SIGNER",
        "organization": "SignCheck Test PKI",
        "country": "ES",
        "certificateSerialNumber": "4F2A..."
      },
      "issuer": "SignCheck Test Intermediate CA",
      "claimedSigningTime": "2026-11-01T10:15:00Z",
      "bestSignatureTime": "2026-11-01T10:15:03Z",
      "certificateChain": [
        { "subject": "CN=TEST SIGNER,...", "issuer": "CN=SignCheck Test Intermediate CA,...",
          "notBefore": "2026-01-01T00:00:00Z", "notAfter": "2028-01-01T00:00:00Z", "trusted": false }
      ],
      "revocation": { "status": "GOOD", "source": "OCSP", "producedAt": "2026-11-01T10:15:04Z" },
      "timestamps": [
        { "type": "SIGNATURE_TIMESTAMP", "productionTime": "2026-11-01T10:15:03Z",
          "tsaName": "Test TSA", "valid": true }
      ],
      "warnings": [],
      "errors": []
    }
  ],
  "rawReports": null
}
```

`rawReports` (only when requested):

```json
{ "simpleReport": "<xml...>", "detailedReport": "<xml...>",
  "diagnosticData": "<xml...>", "etsiValidationReport": "<xml...>" }
```

### `GET /api/v1/trust/status`

```json
{ "mode": "LOTL", "loading": false, "lastSuccessfulRefresh": "2026-11-10T06:00:00Z",
  "trustedListCount": 31, "trustedCertificateCount": 4200 }
```

### `POST /api/v1/signatures` (phase 2)

`multipart/form-data`: `file`, `format` (`PADES|XADES|CADES`), `packaging`
(`ENVELOPED|ENVELOPING|DETACHED`, optional), `level` (`B|T`).
Returns the signed file as `application/octet-stream` with a proper `Content-Disposition`.

### Verdict mapping

| DSS `Indication` | SignCheck `verdict` |
|---|---|
| `TOTAL_PASSED`, `PASSED` | `VALID` |
| `INDETERMINATE` | `INDETERMINATE` |
| `TOTAL_FAILED`, `FAILED` | `INVALID` |

Each DSS `SubIndication` maps to a message key (e.g. `HASH_FAILURE` → "The document was modified after it was signed")
with English and Spanish texts.

### Errors

RFC 9457 `ProblemDetail` with an extra `code`:

| HTTP | `code` | When |
|---|---|---|
| 400 | `NO_FILE` | `file` part missing or empty |
| 413 | `FILE_TOO_LARGE` | file over the configured limit |
| 422 | `NO_SIGNATURE_FOUND` | the file is valid but contains no signature |
| 422 | `UNSUPPORTED_FORMAT` | DSS can't recognise the file as a supported signed document |
| 500 | `VALIDATION_ERROR` | unexpected failure (no stack traces in the response) |

## 5. Non-functional requirements

- **Stateless:** no database. Uploaded files are processed in memory and discarded after the response.
- **Privacy:** never log file contents or certificate personal fields; log only size, format and result.
- **Performance:** validation of a typical PDF (< 5 MB, 1–2 signatures) in under 3 s once trust lists are loaded.
- **Availability of trust data:** LOTL loading happens in the background; while it is loading the API still
  answers and `trust/status` reports `loading: true`.
- **Security:** request size limits, content-type checks, CORS only for the dev frontend origin,
  dependency vulnerability scanning in CI.
- **Portability:** runs with `docker compose up` on any machine, no manual setup.

## 6. Out of scope

- Storing documents or user accounts.
- Signing with user-provided certificates or smart cards in the public demo.
- Server-side signing with real qualified certificates.
