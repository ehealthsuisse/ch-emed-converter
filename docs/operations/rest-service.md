# Operating the REST conversion service

Operator documentation for `converter-rest` (feature 002). API: [openapi.yaml](../../specs/002-rest-conversion-service/contracts/openapi.yaml),
also served by the running service at `GET /api/v1/openapi.yaml`.

## Security model — read this first

> **The service has no authentication, no authorization and no TLS.** Requests and responses
> contain patient data (medication plans and prescriptions). The service **MUST NOT** be reachable
> without protection. Run it behind an API gateway or reverse proxy that terminates TLS and
> controls access, or inside a network segment that only authorized systems can reach.
> (constitution IV, FR-012, FR-013)

The operator is responsible for:

- **Transport security**: TLS between clients and the gateway, and between gateway and service
  if they do not run on the same host or in the same protected network.
- **Access control**: authentication and authorization of the calling systems at the gateway.
- **Gateway logging**: the gateway, proxy or load balancer **must not log request or response
  bodies**. Patient identifier and author details travel in the request body; they never appear in
  URLs or headers, so access logs with URL and headers do not contain them.

## What the service stores

Nothing. It keeps no session, cache, queue or file of medication data beyond the lifetime of a
request (FR-005). Logs go to standard output only; there is no log file.

Each request produces exactly one log line with metadata only, for example:

```text
request timestamp=2026-10-29T13:06:06.202Z correlationId=3685ca5a-… method=POST
route=/api/v1/conversions/{from}/{to} from=ch-emed to=chmed16a status=200 errorType=-
outcome=SUCCESS_WITH_WARNINGS durationMs=6112 requestBytes=284819 documentBytes=213602
```

No request or response content, option values, report messages, patient data or eRezept
signature data is logged (FR-014). HAPI FHIR and the HL7 validator, which log resource content at
DEBUG/TRACE, are held at WARN by `logback-spring.xml`; do not raise them.

Unexpected errors are logged with the correlation id and the exception type only. Clients receive
the same correlation id in the `X-Correlation-Id` header and in the problem body; ask callers to
quote it when they report a problem. A caller may send its own `X-Correlation-Id`
(`[A-Za-z0-9-]{1,64}`); otherwise the service assigns a UUID.

## No outbound connections

The service makes no network calls during conversion (FR-015). Validation runs locally with the IG
packages vendored in the JAR (`ch.fhir.ig.ch-emed#6.0.0`, `ch.fhir.ig.ch-core#6.0.0`); no terminology
server is used. It needs no internet access and can run in a network without egress.

## Running

Requirements: Java 21. Build: `./mvnw -pl converter-rest -am package`. The build produces the
distribution archive `converter-rest/target/ch-emed-converter-rest-<version>-dist.zip`:

| File | Content |
|------|---------|
| `ch-emed-converter-rest.jar`, `lib/` | the service and its dependencies |
| `ch-emed-converter-rest.sh` / `.bat` | start scripts for Linux/macOS and Windows; need `JAVA_HOME` (Java 21+), pass JVM options in `JAVA_OPTS` and all arguments on to the service |
| `rest-service.md` | this document |
| `LICENSE`, `NOTICE`, `THIRD-PARTY.txt`, `third-party-licenses.md`, `README.md` | licences and overview |

```bash
unzip ch-emed-converter-rest-1.0.0-SNAPSHOT-dist.zip
cd ch-emed-converter-rest-1.0.0-SNAPSHOT
JAVA_OPTS="-Xmx2g" ./ch-emed-converter-rest.sh --server.port=8080
```

Without the scripts: `java -jar ch-emed-converter-rest.jar` (in the build:
`java -jar converter-rest/target/ch-emed-converter-rest.jar`).

The JAR (about 60 KB) references its dependencies in `converter-rest/target/lib/` (131 JARs,
about 190 MB: HAPI FHIR, HL7 validator, IG packages, Spring Boot) through its manifest `Class-Path`;
ship the JAR and `lib/` together, side by side, as for the CLI. Building the validation
context takes several seconds at startup; the first conversion after start is slower than the
following ones (observed: about 6 s for a medication card, versus under a second when warm).

Container image (Paketo buildpacks, Java 21; needs a running Docker daemon at build time):

```bash
./mvnw -pl converter-rest -am package -DskipTests
./mvnw -pl converter-rest spring-boot:build-image
docker run --rm -p 8080:8080 ch-emed-converter-rest:1.0.0-SNAPSHOT
```

The image build itself downloads the buildpacks and the JRE; the running container needs no network
access. Verified on 2026-10-29 (Rancher Desktop, Docker Engine 29.5.3): image about 450 MB with
BellSoft Liberica JRE 21.0.13, no shell in the image; started in an internal Docker network without
internet access, it was ready after about 15 s (validation context 12 s), converted, answered
failures and capabilities, and logged no patient data. The image creation date shows 1980 on
purpose (reproducible builds).

Configure the container with environment variables, e.g.
`docker run -e CONVERTER_REST_TIMEOUT=30s -e CONVERTER_REST_MAXCONCURRENT=4 …` (Spring Boot drops
the dashes of property names in environment variables: `max-concurrent` → `MAXCONCURRENT`).

If `docker` reports no server although Rancher Desktop runs, the active Docker context points to
Docker Desktop; use the context `default` (`docker context use default`) or set
`DOCKER_HOST=npipe:////./pipe/docker_engine` for the build.

## Configuration

Standard Spring Boot mechanisms apply (command line `--name=value`, environment variables such as
`CONVERTER_REST_TIMEOUT=20s`, `application.yaml` next to the JAR).

| Property | Default | Meaning |
|----------|---------|---------|
| `converter.rest.max-document-size` | `5MB` | Maximum size of the decoded input document. Larger → `413 REQUEST_TOO_LARGE`. |
| `converter.rest.max-request-size` | `8MB` | Maximum size of the whole request message. Must be ≥ 4/3 × `max-document-size` (base64 adds a third). Larger → `413 REQUEST_TOO_LARGE`, checked before the body is read. |
| `converter.rest.timeout` | `40s` | Time limit of one conversion, including the time in the queue. Exceeded → conversion cancelled, `504 CONVERSION_TIMEOUT`, no partial output. |
| `converter.rest.max-concurrent` | available processors, at most 4 | Conversions running at the same time. Higher values gain no throughput (see Capacity) and slow down each conversion. |
| `converter.rest.queue-size` | 4 × `max-concurrent` | Conversions waiting for a free slot. Full → `503 SERVICE_BUSY` with `Retry-After`. |
| `server.port` | `8080` | HTTP port of the API. |
| `management.server.port` | same as `server.port` | Optional separate port for the health probes. |

Independently of these limits, the library rejects a CHMED16A QR payload that decompresses to more
than 5 MB (`422 INVALID_INPUT`); this library limit is not configurable, as in the CLI.

Invalid configuration (e.g. `max-request-size` too small for `max-document-size`) stops the
service at startup with a message naming the property.

## Health probes

| Endpoint | Meaning |
|----------|---------|
| `GET /actuator/health/liveness` | The JVM and the web server run. Use as liveness probe. |
| `GET /actuator/health/readiness` | `UP` only when the converter and its validation context are built. Use as readiness probe. |

Only the `health` endpoint is exposed; details are never shown.

Readiness stays `DOWN` (HTTP 503) while the converter is being built and if building it failed,
for example because the vendored IG packages are missing or corrupt. In that state conversions
and capabilities answer `503 NOT_READY`; the service never returns unvalidated output (FR-016).
Liveness stays `UP`, so the orchestrator does not restart the instance in a loop: a failed build is
a packaging problem, visible in the startup log (`Converter could not be built: …`).

## Failure responses

Failures are `application/problem+json` (RFC 9457) with an `errorType`; a conversion report is
included whenever a conversion was attempted.

| HTTP | `errorType` | Typical cause |
|------|-------------|---------------|
| 400 | `MALFORMED_REQUEST` | Message not JSON, `document` missing or not base64, unknown property, invalid option value |
| 404 | `UNSUPPORTED_CONVERSION` | Unknown format or pair; the body lists `supportedConversions` |
| 413 | `REQUEST_TOO_LARGE` | Message or decoded document above the limits |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Request not `application/json` |
| 422 | `INVALID_INPUT` | Document cannot be parsed or fails input validation (with report) |
| 422 | `CONVERSION_FAILED` | Strict-mode loss, missing required data, output validation error (with report) |
| 500 | `INTERNAL_ERROR` | Unexpected error; see the log line with the correlation id |
| 503 | `SERVICE_BUSY` | All slots and the queue are taken, or the validation could not be completed under system load (report entry `VALIDATION_INCOMPLETE`); retry after `Retry-After` seconds |
| 503 | `NOT_READY` | Converter not (yet) built; see readiness |
| 504 | `CONVERSION_TIMEOUT` | Conversion exceeded `converter.rest.timeout` |

## Capacity

`GET /api/v1/capabilities` returns the supported conversions, options, pinned specification
versions and the configured limits of the running instance. Size `max-concurrent` to the CPU
cores available to the service; requests beyond `max-concurrent + queue-size` are rejected
immediately with 503 rather than piling up.

Measured on a 16-core development machine (warm service). Almost all of the time is the HL7
validation of the produced CH EMED document; the REST layer itself adds no measurable time.

- One conversion: 3–4 s for 20 different medications, a realistic large plan; about 19 s for 100,
  the upper bound.
- Concurrency: 20 simultaneous 20-medication plans took about 24 s in total, whether 4, 8 or 16
  ran at the same time (about 0.8 plans per second). More than four only make each conversion
  slower: with 4 the answers arrived after 7–24 s, with 16 after 18–23 s. Data is never mixed
  between requests.

Targets of the spec (revised 2026-11-01): 20 different medications under 5 s and 100 under 30 s end
to end, with less than 1 s added by the service (SC-002); 20 simultaneous requests with 20
medications each answered with p95 under 30 s, or rejected at once with `SERVICE_BUSY` beyond
`max-concurrent` + `queue-size` (SC-003).

**CPU starvation.** The HL7 validator aborts its pattern checks after a fixed 500 ms of wall-clock
time. If the service does not get enough CPU (host overloaded by other processes, container CPU
limit below `max-concurrent`), valid documents would fail validation. The service retries such a
validation twice and otherwise answers `503 SERVICE_BUSY` with `VALIDATION_INCOMPLETE` in the
report instead of declaring the document invalid. Keep `max-concurrent` at or below the CPUs
actually available to the service (in a container: the CPU limit, not the host's cores) and do
not co-locate CPU-heavy workloads. Repeated `validation hit the HL7 pattern time limit` warnings
in the log mean the service is starved.

The default `converter.rest.timeout` of 40 s includes the time in the queue and lies above the 30 s
targets. The default limits accept 20 simultaneous conversions (4 running, 16 queued); raise
`queue-size` and `timeout` together if callers send larger bursts and can wait.
