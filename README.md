# SignCheck

![Status: work in progress](https://img.shields.io/badge/status-work%20in%20progress-orange)
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

Stack: Java 21, Spring Boot 4.1, springdoc-openapi 3.2.

## Documentation

- [Specification](docs/SPEC.md) — functional requirements and REST API contract
- [Architecture](docs/ARCHITECTURE.md) — modules, DSS integration and test PKI
- [Roadmap](docs/ROADMAP.md) — phases, tasks and current progress

## License

[Apache License 2.0](LICENSE). SignCheck uses the EU DSS library, which is licensed under LGPL-2.1.
