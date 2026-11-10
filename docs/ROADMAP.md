# SignCheck — Roadmap

Work top to bottom, one task at a time. A task is done when all its acceptance criteria hold
and `./mvnw verify` (and the frontend checks, once the frontend exists) pass. Tick the box when done.

## Phase 0 — Project setup

- [x] **0.1 Repository basics**
  - `.gitignore` (Java, Maven, Node, IDEs, OS files), `LICENSE` (MIT or Apache-2.0, Pedro decides),
    `.editorconfig`, skeleton `README.md` with project goal and "work in progress" badge.
  - Done when: repo has those files and the README explains the goal in 3–5 lines.
- [x] **0.2 Backend skeleton**
  - Spring Boot app in `backend/` with Maven wrapper, Java 21, package `dev.signcheck`,
    `spring-boot-starter-web`, `validation`, `actuator`, `springdoc-openapi`, test starter.
  - `SignCheckProperties` with the structure from ARCHITECTURE §3 (values not used yet).
  - Profiles `dev` and `test`.
  - Done when: `./mvnw verify` passes, `/actuator/health` returns UP, Swagger UI loads, one context-load test.
- [ ] **0.3 CI**
  - GitHub Actions workflow running `./mvnw verify` on push and PR; Dependabot for Maven and GitHub Actions.
  - Done when: workflow file is valid and documented in the README.

## Phase 1 — Test PKI and first validation (PAdES)

- [ ] **1.1 DSS dependencies**
  - Add `dss-bom` (latest 6.x with TLv6 support) and the PAdES-related modules.
  - Done when: build passes and the chosen DSS version and modules are noted in the README.
- [ ] **1.2 Test PKI**
  - `TestPki` (BouncyCastle): root, intermediate, signers `valid`, `expired`, `revoked`, untrusted root + signer, CRL.
  - Unit tests asserting the certificates have the expected validity, key usage and chain.
  - Done when: tests pass and the class Javadoc explains what each certificate is for.
- [ ] **1.3 Signed sample generation (PAdES)**
  - `SignedSamples.padesBaselineB(...)` using `PAdESService` and a PKCS#12/in-memory token from the test PKI,
    plus `tamper(byte[])` helper.
  - Done when: a test produces a signed PDF that DSS can parse.
- [ ] **1.4 Validation service + endpoint (PAdES only)**
  - `DssConfig` with a `CertificateVerifier` bean for mode `NONE`; test config using the test PKI verifier.
  - `ValidationService`, `ValidationReportMapper` (verdict, level, signer, signing time, errors/warnings),
    `ValidationController` (`POST /api/v1/validations`).
  - Done when: MockMvc tests cover valid, tampered and untrusted PAdES with the expected verdicts.
- [ ] **1.5 Error handling**
  - `GlobalExceptionHandler` with `ProblemDetail` + `code`: `NO_FILE`, `FILE_TOO_LARGE`,
    `NO_SIGNATURE_FOUND`, `UNSUPPORTED_FORMAT`, `VALIDATION_ERROR`.
  - Done when: each error has a MockMvc test asserting status and `code`.

## Phase 2 — XAdES, CAdES and detached signatures

- [ ] **2.1 XAdES** — samples enveloped, enveloping, detached; tests for valid and tampered.
- [ ] **2.2 CAdES** — samples attached and detached; tests for valid and tampered.
- [ ] **2.3 Detached support in the API** — `originals` multipart part → `setDetachedContents`.
  Tests: detached without originals (`INDETERMINATE`), with wrong original (`INVALID`), with right original (`VALID`).
- [ ] **2.4 Multiple signatures** — PDF with two signatures, one broken; summary and per-signature results correct.
- [ ] **2.5 Full validation matrix** — every case in ARCHITECTURE §5 has a test. Expired and revoked signers included.

## Phase 3 — Trust and revocation

- [ ] **3.1 Trust modes** — `TrustMode` `NONE | KEYSTORE | LOTL`; verifier built from config.
  Done when: properties tests and an integration test per mode (LOTL mocked/offline).
- [ ] **3.2 LOTL job** — `TrustListService` with `TLValidationJob`, file cache, async startup load,
  scheduled refresh, LOTL signature verification as per DSS docs, TLv5 + TLv6 accepted.
  Done when: unit/integration tests with an offline loader; manual run in `docker` profile loads the EU LOTL (documented).
- [ ] **3.3 Online revocation** — OCSP + CRL sources with configurable timeouts, only in `LOTL` (and optionally `KEYSTORE`).
- [ ] **3.4 Trust status endpoint** — `GET /api/v1/trust/status` with loading flag, last refresh, counts.
- [ ] **3.5 Richer report** — certificate chain, revocation info, timestamps, qualification, best signature time.
  Mapper unit tests for each field.

## Phase 4 — API polish

- [ ] **4.1 Raw reports** — `includeRawReports=true` returns simple, detailed, diagnostic and ETSI reports as XML strings.
- [ ] **4.2 Human messages** — `SubIndication` → message keys, English + Spanish (`messages.properties`, `messages_es.properties`, `Accept-Language`).
- [ ] **4.3 OpenAPI** — descriptions, examples and error schemas for every endpoint.
- [ ] **4.4 Hardening** — content-type checks, size limits, no sensitive data in logs (test with a log appender).

## Phase 5 — Frontend

- [ ] **5.1 Angular skeleton** — strict mode, routing, proxy to backend, CI workflow for the frontend.
- [ ] **5.2 Upload page** — drop zone (keyboard accessible), optional originals, loading and error states.
- [ ] **5.3 Result page** — summary banner, signature cards, technical details panel, raw report and JSON downloads.
- [ ] **5.4 Trust status indicator** in the header.
- [ ] **5.5 i18n** — English and Spanish, language switcher.
- [ ] **5.6 Tests** — services and main components.

## Phase 6 — Delivery

- [ ] **6.1 Dockerfiles + docker-compose** (non-root images, TL cache volume, nginx `/api` proxy).
- [ ] **6.2 Smoke test in CI** with `docker compose`.
- [ ] **6.3 README (English)** — what/why, screenshots or GIF, quick start, API examples with `curl`,
  architecture diagram, key decisions (ARCHITECTURE §8), testing approach, roadmap status, licence and DSS credit.
- [ ] **6.4 Public demo (optional)** — deploy, link in README.

## Phase 7 — Signing (phase 2 of the product)

- [ ] **7.1 Demo CA tooling** — generate `demo-ca.p12` and demo signer, clearly marked TEST, documented.
- [ ] **7.2 Signing service** — PAdES / XAdES / CAdES, packaging, level B.
- [ ] **7.3 Level T** — configurable TSA; in tests an in-memory TSP from the test PKI.
- [ ] **7.4 Endpoint** `POST /api/v1/signatures` + tests (sign → validate round trip is `VALID` in `KEYSTORE` mode).
- [ ] **7.5 Frontend "Sign" page**.

## Phase 8 — Extras (pick freely)

- [ ] ASiC-S / ASiC-E validation
- [ ] Batch validation
- [ ] Human-friendly PDF report
- [ ] Custom validation policy file
- [ ] CLI client (`signcheck validate file.pdf`)
