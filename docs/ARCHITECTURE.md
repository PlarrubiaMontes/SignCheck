# SignCheck — Architecture

## 1. Overview

```
┌──────────────┐      /api/*       ┌───────────────────────────────────────────┐
│  Angular UI  │ ────────────────▶ │  Spring Boot backend                      │
│  (nginx)     │ ◀──────────────── │                                           │
└──────────────┘       JSON        │  api ──▶ validation ──▶ DSS validator     │
                                   │              │                            │
                                   │              ▼                            │
                                   │  trust (LOTL job, trusted cert source)    │
                                   │              │                            │
                                   │  signing (phase 2) ──▶ DSS services       │
                                   └──────────────┼────────────────────────────┘
                                                  │ (only outside tests)
                                   EU LOTL / national TLs, OCSP, CRL, TSA
```

- **Stateless** backend: one request = one in-memory validation. No database.
- **Frontend** is a static Angular build served by nginx, which also proxies `/api` to the backend.
- All network access to trust and revocation infrastructure goes through DSS data loaders that are
  configured in one place (`config`) and replaced by offline sources in tests.

## 2. Backend packages

Base package: `dev.signcheck`

```
dev.signcheck
├── SignCheckApplication
├── config/
│   ├── SignCheckProperties        # @ConfigurationProperties("signcheck")
│   ├── DssConfig                  # CertificateVerifier, data loaders, revocation sources
│   ├── OpenApiConfig
│   └── WebConfig                  # CORS (dev), multipart limits
├── api/
│   ├── error/                     # GlobalExceptionHandler, ErrorCode, domain exceptions
│   └── (shared DTOs if any)
├── validation/
│   ├── ValidationController       # POST /api/v1/validations
│   ├── ValidationService          # orchestrates: build DSSDocument(s) → validator → mapper
│   ├── DocumentValidatorFactory   # wraps SignedDocumentValidator.fromDocument(...)
│   ├── ValidationReportMapper     # DSS Reports → our DTOs (the core of the "no DSS leak" rule)
│   ├── VerdictMessages            # SubIndication → message key
│   └── dto/                       # ValidationResponse, SignatureResult, SignerInfo, ...
├── trust/
│   ├── TrustController            # GET /api/v1/trust/status
│   ├── TrustListService           # LOTL job, async load on startup + scheduled refresh
│   ├── TrustMode                  # NONE | KEYSTORE | LOTL
│   └── dto/TrustStatus
└── signing/                       # phase 2
    ├── SigningController          # POST /api/v1/signatures
    ├── SigningService             # PAdESService / XAdESService / CAdESService
    └── DemoSignerProvider         # demo PKCS#12 token from the app's own test CA
```

Rules:

- Controllers only translate HTTP ↔ DTOs and call one service method.
- `validation` and `signing` never build DSS network objects themselves; they receive a configured
  `CertificateVerifier` bean.
- Mapping from DSS reports to DTOs is isolated in `ValidationReportMapper` and heavily unit-tested.

## 3. DSS integration

### Dependencies

Import `eu.europa.ec.joinup.sd-dss:dss-bom` (latest 6.x) in `dependencyManagement`, then add only the modules needed.
Check exact artifact names in the DSS documentation for the chosen version. Expected set:

- Formats: `dss-pades`, `dss-pades-pdfbox`, `dss-xades`, `dss-cades`, (`dss-asic-xades`, `dss-asic-cades` later)
- Validation and trust: `dss-validation` (if separate in the chosen version), `dss-tsl-validation`
- Required implementations: `dss-utils-apache-commons` (or the Guava one) and a CRL parser
  (`dss-crl-parser-stream` or `dss-crl-parser-x509crl`)
- Phase 2: `dss-token` (PKCS#12 signing token), `dss-service` (online TSP source)

DSS is published on Maven Central; if an artifact is missing there, use the EC repository documented by DSS.

### Validation flow

1. Wrap the uploaded bytes in `InMemoryDocument` (with the original file name).
2. `SignedDocumentValidator.fromDocument(document)` — DSS detects PAdES / XAdES / CAdES / ASiC.
   If no validator accepts the document → `UNSUPPORTED_FORMAT`.
3. If originals were uploaded → `validator.setDetachedContents(originals)`.
4. `validator.setCertificateVerifier(certificateVerifier)` (shared, configured bean).
5. `Reports reports = validator.validateDocument()` (default ETSI validation policy;
   later, optionally a custom policy file in `resources/policy/`).
6. If `reports.getSimpleReport().getSignaturesCount() == 0` → `NO_SIGNATURE_FOUND`.
7. Map `SimpleReport` + `DiagnosticData` (+ `DetailedReport` where needed) → `ValidationResponse`.
8. If raw reports were requested, marshal them with the DSS facades (`reports.getXml...()`).

### Certificate verifier

One `CommonCertificateVerifier` bean configured according to `signcheck.trust.mode`:

| Mode | Trusted sources | Revocation | Use |
|---|---|---|---|
| `NONE` | none (everything ends `INDETERMINATE` / no trust anchor) | offline | dev quick start, some tests |
| `KEYSTORE` | `CommonTrustedCertificateSource` loaded from a configured PKCS#12 (e.g. the demo CA) | offline or online | demo of the signing feature, integration tests |
| `LOTL` | `TrustedListsCertificateSource` filled by the LOTL job (+ optional keystore) | online OCSP + CRL | production / real documents |

Online revocation uses `OnlineOCSPSource` and `OnlineCRLSource` with a `CommonsDataLoader`
(timeouts configurable). AIA fetching of missing intermediates: enabled only in `LOTL` mode.

### Trust lists (LOTL)

- Source: the EU List of Trusted Lists (URL configurable, default the official EU LOTL).
- The LOTL is signed; its signing certificates must be verified as documented by DSS
  (official journal keystore / pivot support). Follow the DSS "TL validation" chapter for the chosen version —
  do not disable signature checks of the LOTL.
- Must work with **Trusted List v6** (mandatory from 29 April 2026): use a DSS version that supports it and
  the matching validation policy (accept TL version 5 and 6).
- `TLValidationJob` with `FileCacheDataLoader` (online) and cache directory `signcheck.trust.cache-dir`
  (a Docker volume in production).
- Load **asynchronously** at startup (application keeps answering), then refresh on a schedule
  (`signcheck.trust.refresh-cron`, default every 6 h). Expose progress via `TrustListService`.

### Configuration (application.yml)

```yaml
signcheck:
  upload:
    max-file-size: 20MB
  trust:
    mode: NONE            # NONE | KEYSTORE | LOTL
    lotl-url: https://ec.europa.eu/tools/lotl/eu-lotl.xml
    cache-dir: ${java.io.tmpdir}/signcheck-tl-cache
    refresh-cron: "0 0 */6 * * *"
    keystore:
      path: classpath:trust/demo-ca.p12   # test-only material, generated
      password: changeit-test-only
  network:
    connect-timeout: 5s
    read-timeout: 10s
  signing:
    enabled: false
    tsa-url: ""           # public TSA for level T (phase 2)
```

Profiles: `dev` (mode `NONE`, CORS for `http://localhost:4200`), `docker` (mode `LOTL`), `test`.

## 4. Test PKI

All signed test material is **generated**, never committed from the outside world.

`src/test/java/dev/signcheck/testpki/` contains:

- `TestPki` — builds with BouncyCastle, deterministically per test run:
  - Root CA → Intermediate CA
  - Signer certificates: `valid`, `expired`, `notYetValid`, `revoked`, `wrongKeyUsage`
  - An untrusted root with its own signer (`untrusted`)
  - A CRL from the intermediate listing `revoked`
- `TestPkiCertificateVerifierFactory` — `CertificateVerifier` that trusts the test root and uses the
  generated CRL as an offline revocation source (no network).
- `SignedSamples` — produces signed documents on the fly with DSS services and the test PKI:
  PAdES (B, T with a test TSP if needed), XAdES enveloped / enveloping / detached, CAdES attached / detached,
  plus tampered copies (one byte changed after signing).

For tests needing timestamps, implement a small in-memory `TSPSource` signed by a test TSA certificate
from the same test PKI.

For the **phase 2 demo signer**, a script or a one-off `main` in `tools/` generates `demo-ca.p12`
and the demo signer PKCS#12. Their passwords are explicitly marked "test only" and the certificates
contain `TEST` in their subject so they can never be confused with real ones.

## 5. Testing strategy

| Level | What | Tools |
|---|---|---|
| Unit | `ValidationReportMapper`, verdict/message mapping, properties binding | JUnit 5, AssertJ |
| Integration (backend) | real DSS validation of generated samples for every format and failure case | `@SpringBootTest` (profile `test`), MockMvc |
| API contract | status codes, `ProblemDetail` bodies, multipart handling, size limits | MockMvc |
| Frontend | components and services | Angular test runner (default of the chosen Angular version) |
| Smoke | stack starts and answers `/actuator/health` | GitHub Actions job with `docker compose` |

Minimum validation matrix (all must have a test):

| Case | Expected verdict |
|---|---|
| valid signature, trusted test root | `VALID` |
| document modified after signing | `INVALID` (`HASH_FAILURE`) |
| expired signer certificate | `INDETERMINATE` or `INVALID` per policy (assert the sub-indication) |
| revoked signer certificate | `INVALID` / `INDETERMINATE` per policy (`REVOKED...`) |
| untrusted root | `INDETERMINATE` (`NO_CERTIFICATE_CHAIN_FOUND`) |
| detached signature without originals | `INDETERMINATE` (`SIGNED_DATA_NOT_FOUND`) |
| detached signature with wrong original | `INVALID` (`HASH_FAILURE`) |
| PDF with two signatures, one broken | summary `INVALID` / `INDETERMINATE`, per-signature results correct |
| unsigned PDF | 422 `NO_SIGNATURE_FOUND` |
| random bytes | 422 `UNSUPPORTED_FORMAT` |

Exact DSS sub-indications depend on the validation policy and DSS version; tests assert the observed value
and the test name documents the expected behaviour.

## 6. Frontend

```
frontend/src/app/
├── core/
│   ├── api/            # DTO interfaces + ValidationApiService, TrustApiService
│   └── i18n/
├── features/
│   ├── validate/       # upload page: drop zone, originals toggle, submit
│   ├── result/         # summary banner + one card per signature + expandable technical details
│   └── sign/           # phase 2
└── shared/             # verdict badge, file size pipe, date pipe...
```

UX:

- Big drop zone; optional second drop zone "Original document (for detached signatures)".
- Result: summary banner (green / amber / red), then one card per signature with signer, time, level,
  and a plain-language reason. A "Technical details" panel shows chain, revocation, timestamps, warnings.
- Buttons to download raw reports (XML) and the JSON response.
- Trust status indicator in the header ("Trust lists loading…" / "EU trust lists loaded at 06:00").
- Accessible: keyboard-operable drop zone, colour is never the only signal (icon + text).

## 7. Deployment and CI

- `backend/Dockerfile`: multi-stage (Maven build with cache → JRE 21 slim runtime, non-root user).
- `frontend/Dockerfile`: multi-stage (Node build → nginx with `/api` proxy config).
- `docker-compose.yml`: `backend` (profile `docker`, volume for TL cache) + `frontend` on port 8081.
- GitHub Actions:
  - `backend.yml`: `./mvnw verify` on Java 21, cache Maven.
  - `frontend.yml`: `npm ci`, `npm test` (headless), `npm run build`.
  - Dependency scanning (Dependabot config).
- Optional public demo on a free container host in `LOTL` mode.

## 8. Key decisions (to keep in the README as ADR summaries)

1. **DSS instead of hand-written crypto** — reference implementation of ETSI standards; focus our effort on API, UX and quality.
2. **Own DTOs over DSS types** — stable API, decoupled from library upgrades, easier to document.
3. **Stateless, no persistence** — privacy by design; nothing to secure at rest.
4. **Generated test PKI** — reproducible tests, no real personal data, no internet in CI.
5. **Trust modes** — the app is useful offline (dev/demo) and fully eIDAS-aware with the LOTL.
