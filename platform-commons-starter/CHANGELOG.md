# Changelog

All notable changes to `platform-commons-starter` are recorded here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and version numbers follow [SemVer](https://semver.org/).

## [Unreleased]

### Added

- Inbound per-client rate limit (`platform.rate-limit.*`): 100 requests per
  minute by default, 429 with `Retry-After` when exceeded. Actuator paths
  are excluded so probes do not consume the budget.

## [1.0.0] — 2026-10-07

First public release. Published to GitHub Packages on tag `v1.0.0`.

### Added

- Correlation-id servlet filter and outgoing `RestClient` interceptor
  (`platform.correlation.*`)
- Shared `ApiError` body and `PlatformExceptionHandler` for validation and
  unexpected failures (`platform.error-handling.*`)
- Pre-configured `RestClient` with connect/read timeouts
- Resilience4j retry on that client (`platform.rest-client.retry.*`); POST and
  PATCH are not retried unless opted in
- Micrometer common tags `service` and `environment` (`platform.metrics.*`)
- Timer `platform.http.client.requests` on outgoing `RestClient` calls
- `micrometer-registry-prometheus` so `/actuator/prometheus` can be scraped
- Kafka producer defaults: `acks=all`, idempotence, `max.in.flight=5`
- Consumer `DefaultErrorHandler` publishing failed records to `<topic>.DLT`
- Kafka `HealthIndicator` (`platform.kafka.health.timeout`)
- Opt-in JSON logging via `classpath:logback-platform-json.xml`
- CycloneDX SBOM (`target/bom.json`) and OWASP dependency-check on verify
  (skipped locally; CI enables it)

[1.0.0]: https://github.com/saimsohail1/Event-Driven-Microservices-Platform/releases/tag/v1.0.0
