# SignCheck

![Status: work in progress](https://img.shields.io/badge/status-work%20in%20progress-orange)
[![Frontend CI](https://github.com/PlarrubiaMontes/SignCheck/actions/workflows/frontend-ci.yml/badge.svg)](https://github.com/PlarrubiaMontes/SignCheck/actions/workflows/frontend-ci.yml)
[![Backend CI](https://github.com/PlarrubiaMontes/SignCheck/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/PlarrubiaMontes/SignCheck/actions/workflows/backend-ci.yml)
![License: Apache-2.0](https://img.shields.io/badge/license-Apache--2.0-blue)

SignCheck is an open-source web application that validates advanced electronic signatures
(**PAdES**, **XAdES** and **CAdES**) according to the eIDAS regulation and ETSI standards.
Upload a signed document and get a clear verdict (valid, invalid or indeterminate) plus the
technical details behind it: signer, signing time, signature level, certificate chain and trust status.
It is built on the European Commission's [DSS (Digital Signature Services)](https://github.com/esig/dss) library,
and a later phase adds signature creation with self-generated test certificates.

## Quick start (backend)

Requires Java 21. Maven is downloaded by the wrapper.

```bash
cd backend
./mvnw verify            # build + tests
./mvnw spring-boot:run   # http://localhost:8080 (profile "dev")
```

- Health: <http://localhost:8080/actuator/health>
- Swagger UI: <http://localhost:8080/swagger-ui.html>

Validate a document (add `-F originals=@original.txt` for detached signatures, `-F includeRawReports=true`
for the DSS XML reports, `-H 'Accept-Language: es'` for Spanish explanations):

```bash
curl -F file=@contract-signed.pdf http://localhost:8080/api/v1/validations
curl http://localhost:8080/api/v1/trust/status
```

### Try it with generated samples

No real signed document is needed: the test PKI can write TEST-ONLY samples to `backend/target/samples`.

```bash
cd backend
./mvnw test -Dtest=SampleFilesGenerator -Dsamples=true   # writes target/samples/*
./mvnw spring-boot:run                                   # profile "dev", trust mode NONE
curl -F file=@target/samples/pades-tampered.pdf -H 'Accept-Language: es' http://localhost:8080/api/v1/validations
```

| Sample | Expected in mode `NONE` |
|---|---|
| `pades-tampered.pdf` | `INVALID` / `HASH_FAILURE` |
| `pades-valid.pdf`, `xades-enveloped.xml`, `cades-attached.p7m` | `INDETERMINATE` / `NO_CERTIFICATE_CHAIN_FOUND` (nothing is trusted) |
| `xades-detached.xml` or `cades-detached.p7s` without `-F originals=@target/samples/original.txt` | `INDETERMINATE` / `SIGNED_DATA_NOT_FOUND` |
| `pades-two-signatures-one-broken.pdf` | first `INDETERMINATE`, second `INVALID` / `SIG_CRYPTO_FAILURE` |
| `unsigned.pdf` | HTTP 422, `code: NO_SIGNATURE_FOUND` |

`VALID` results need the test root as trust anchor *and* its CRLs, which only the automated tests serve (from an
in-memory network): see `PadesValidationTest`. The demo CA of the signing phase will make `VALID` reachable by hand.

## Frontend

Angular 22 (standalone components, signals, zoneless, strict TypeScript), plain SCSS with light and dark themes.
Requires Node.js 22.22+, 24.15+ or 26 (see `frontend/.nvmrc`).

```bash
cd frontend
npm ci
npm start        # http://localhost:4200, proxies /api to the backend on :8080
npm test         # Vitest unit tests
npm run build
```

- Upload page with a keyboard-accessible drop zone, optional originals for detached signatures and optional raw reports.
- Result page: summary banner, one card per signature with a plain-language reason, technical details
  (chain, revocation, timestamps, warnings) and downloads of the JSON result and the DSS XML reports.
- Trust status in the header (polls while the EU trusted lists load); English / Spanish switcher. The language is
  also sent as `Accept-Language`, so the backend explains verdicts in the same language. Verdicts always use an
  icon and a word, never colour alone.

## Trust modes

A signature can only be `VALID` if its certificate chain ends in a trusted certificate. `signcheck.trust.mode`
decides where those come from:

| Mode | Trust anchors | Revocation (OCSP/CRL) | Use |
|---|---|---|---|
| `NONE` (profile `dev`) | none: best verdict is `INDETERMINATE` | offline | local development |
| `KEYSTORE` | certificates in `signcheck.trust.keystore` (PKCS#12) | online | demo CA, integration tests |
| `LOTL` (profile `docker`) | EU Trusted Lists (+ keystore if present) | online, plus AIA | real eIDAS documents |

`LOTL` mode downloads the EU List of Trusted Lists in the background (`GET /api/v1/trust/status` shows the
progress), accepts Trusted Lists v5 and v6 and ignores any list whose signature or freshness check fails.
The LOTL signature is checked against the signing certificates announced in the Official Journal of the EU.
Those are real EU certificates, so they are **not** in this repository: provide them as a PKCS#12 file with
`signcheck.trust.lotl-keystore.path` / `.password` (DSS publishes such a keystore with its demos).

All network access (Trusted Lists, CRL, OCSP, AIA) goes through one bean, `NetworkDataLoaders`, with the
timeouts of `signcheck.network.*`. Tests replace it with an in-memory network, so they never need internet.

Stack: Java 21, Spring Boot 4.1, springdoc-openapi 3.2, EU DSS 6.5.1.

## DSS modules

SignCheck uses [EU DSS](https://github.com/esig/dss) **6.5.1**, the latest release on Maven Central. DSS 6.x
uses the `jakarta.*` namespace that Spring Boot 4 requires, and 6.5.1 validates Trusted Lists v5 and v6
(ETSI TS 119 612 v2.4.1). Versions come from `dss-bom`. Only the modules in use are declared:

| Module | Why |
|---|---|
| `dss-validation` | signature validation process and reports |
| `dss-policy-jaxb` | loads the default ETSI validation policy |
| `dss-pades` + `dss-pades-pdfbox` | PAdES, with PDFBox as PDF backend (the reference implementation; OpenPDF is the alternative) |
| `dss-xades` | XAdES (XML signatures; brings Apache Santuario) |
| `dss-cades` | CAdES (CMS signatures, `.p7m` / `.p7s`) |
| `dss-cms-object` | CMS implementation (in memory; `dss-cms-stream` is still experimental) |
| `dss-utils-apache-commons` | implementation of DSS utils (the alternative is Guava) |
| `dss-crl-parser-stream` | streaming CRL parser, lighter on memory than the `X509CRL` one for large CRLs |

Trusted List modules are added by the roadmap task that uses them.
`DssClasspathTest` checks that exactly one implementation of each pluggable DSS service is on the classpath.

`dss-bom` also inherits third-party versions from its parent POM (JUnit 5, JAXB 3...). The `pom.xml` imports
Spring Boot's BOM before it, so Spring Boot keeps control of shared libraries and DSS only decides its own
modules and libraries Spring Boot does not manage (BouncyCastle, PDFBox).

## Continuous integration

- **Frontend CI** ([`.github/workflows/frontend-ci.yml`](.github/workflows/frontend-ci.yml)) runs `npm ci`,
  `npm test` and `npm run build` for changes under `frontend/`.
- **Backend CI** ([`.github/workflows/backend-ci.yml`](.github/workflows/backend-ci.yml)) runs
  `./mvnw verify` on Temurin 21 for every push and pull request to `main` that touches `backend/`.
  Maven dependencies are cached, and Surefire reports are uploaded as an artifact when tests fail.
- **Dependabot** ([`.github/dependabot.yml`](.github/dependabot.yml)) checks Maven dependencies and
  GitHub Actions and npm weekly; minor and patch updates are grouped into one pull request per ecosystem.

## Documentation

- [Specification](docs/SPEC.md) — functional requirements and REST API contract
- [Architecture](docs/ARCHITECTURE.md) — modules, DSS integration and test PKI
- [Roadmap](docs/ROADMAP.md) — phases, tasks and current progress

## License

[Apache License 2.0](LICENSE). SignCheck uses the EU DSS library, which is licensed under LGPL-2.1.
