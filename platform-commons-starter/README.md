# platform-commons-starter

Shared Spring Boot auto-configuration for the platform services. Add one
dependency and you get the conventions every service is expected to follow:
correlation IDs, a single error shape, a timed and retried `RestClient`,
Micrometer common tags plus Prometheus, an idempotent Kafka producer, dead-letter
consumer handling, and a Kafka health indicator.

Coordinates: `org.example:platform-commons-starter:1.0.0`

## What it provides

| Concern | What you get | How to turn it off |
| --- | --- | --- |
| Correlation ID | `X-Correlation-Id` inbound, echoed outbound, copied onto outgoing `RestClient` calls, published to MDC as `correlationId` | `platform.correlation.enabled=false` |
| Error body | `PlatformExceptionHandler` returns [`ApiError`](src/main/java/org/platform/commons/web/ApiError.java) for validation failures and unexpected 500s | `platform.error-handling.enabled=false` |
| HTTP client | A `RestClient` bean with connect/read timeouts | Define your own `RestClient` |
| Client retry | Resilience4j: 3 attempts, 200 ms apart, `IOException` and 5xx only; POST/PATCH are not retried | `platform.rest-client.retry.enabled=false` |
| Client timer | `platform.http.client.requests` (method, host, status, outcome — never the URI path) | Present only when a `MeterRegistry` exists |
| Common tags | Every meter is tagged `service` + `environment` | `platform.metrics.enabled=false` |
| Prometheus | `micrometer-registry-prometheus` on the classpath | Exclude the registry, or omit `prometheus` from actuator exposure |
| Kafka producer | `acks=all`, `enable.idempotence=true`, `max.in.flight=5` if you have not set any of the three | `platform.kafka.producer.apply-defaults=false` |
| Kafka consumer | `DefaultErrorHandler` → `<topic>.DLT` after 3 deliveries | Define any `CommonErrorHandler`, or `platform.kafka.enabled=false` |
| Kafka health | `/actuator/health` entry `kafka`, 2 s timeout | `management.health.kafka.enabled=false` |
| JSON logs | `logback-platform-json.xml` | Opt-in only (see below) |

Every bean is `@ConditionalOnMissingBean` (or conditional on a specific bean
name). Kafka auto-configuration is `@ConditionalOnClass`, and `spring-kafka` is
an optional dependency: a service that does not use Kafka does not get Kafka
beans.

## How to use it

### In this repository

Install the starter into the local Maven repo, then build a service — or build
everything from the repo root:

```bash
# from the repo root
mvn -B verify
```

```xml
<dependency>
    <groupId>org.example</groupId>
    <artifactId>platform-commons-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

Set `spring.application.name` (used as the `service` metric tag) and expose
Prometheus next to the probes you already have:

```yaml
spring:
  application:
    name: order-service

platform:
  metrics:
    environment: ${PLATFORM_ENV:local}

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

### From GitHub Packages (tagged releases)

Releases are published to GitHub Packages when a `v*` tag matching the POM
version is pushed (for example `v1.0.0`).

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/saimsohail1/Event-Driven-Microservices-Platform</url>
    </repository>
</repositories>
```

GitHub Packages requires a token with `read:packages` even for public
packages. In `~/.m2/settings.xml`:

```xml
<server>
    <id>github</id>
    <username>YOUR_GITHUB_USERNAME</username>
    <password>${env.GITHUB_TOKEN}</password>
</server>
```

### Domain exceptions stay in the service

The starter's advice is `@Order(LOWEST_PRECEDENCE)` so a service handler for
`UnknownProductException` or `PaymentAlreadyExistsException` still wins. Keep
those handlers; delete only the duplicated validation / 500 mapping. Return
`ApiError` so the body shape stays one thing.

### Kafka not-retryable exceptions

```yaml
platform:
  kafka:
    consumer:
      non-retryable-exceptions:
        - org.inventoryservice.exception.UnknownProductException
        - org.inventoryservice.exception.InsufficientStockException
```

A class name that does not load is skipped (and logged). A typo must not stop
the service booting.

### JSON logging

Logback initialises before Spring, so a library cannot force a log format.
Opt in:

```yaml
logging:
  config: classpath:logback-platform-json.xml
```

## How to override any default

| You want to… | Do this |
| --- | --- |
| Change a timeout, retry count, DLT suffix, health timeout, tag value | Set the matching `platform.*` property |
| Replace a bean the starter created | Declare your own bean of the same type (or the documented name: `platformRestClientRetry`, `platformCommonTagsCustomizer`, `deadLetterProducer`, `kafkaHealthIndicator`) |
| Keep a producer setting you chose on purpose | Set `acks`, `enable.idempotence` or `max.in.flight` under `spring.kafka.producer` — the starter then leaves all three alone |
| Disable a whole feature | `platform.<feature>.enabled=false` |
| Disable Kafka health the Actuator way | `management.health.kafka.enabled=false` |

The generated `META-INF/spring-configuration-metadata.json` lists every
`platform.*` key and its default, so IDEs complete them.

## Versioning

The starter follows [SemVer](https://semver.org/). The first public release is
`1.0.0`.

**Patch** (`1.0.x`) — bug fixes that do not change a default, a property name,
a bean name, or a response / metric shape.

**Minor** (`1.x.0`) — new properties, new beans, new auto-configurations. Existing
defaults stay the same. A service that does nothing keeps behaving the same.

**Major** (`x.0.0`) — a breaking change. That includes any of:

- renaming or removing a `platform.*` property
- changing a default in a way a running service would notice (retry count,
  timeout, DLT suffix, header name, metric name, tag key, error JSON field)
- renaming a public bean (`platformRestClient`, `kafkaHealthIndicator`, …)
- changing `ApiError` or the Prometheus scrape shape
- dropping a supported Spring Boot generation

Tag the repo `v<pom-version>` to publish. The workflow refuses a tag that does
not match `project.version` in the POM.

See [CHANGELOG.md](CHANGELOG.md).

## Building this module

```bash
mvn -B verify
# SBOM: target/bom.json
# CVE report (slow; downloads the NVD):
mvn -B verify -Ddependency-check.skip=false
```
