# Changelog

Notable changes are documented here. This project follows semantic versioning for tagged releases.

## Unreleased

### Added

- Process-local `Idempotency-Key` support with retained-job replay and conflicting-option rejection.
- Configurable active-job and history limits, terminal-record expiry, and HTTP 503 capacity responses.
- Concurrent admission, HTTP failure, and duplicate-delivery tests, plus replay checks in the packaged-JAR demo.
- A runnable retry and overload walkthrough in `docs/job-admission.md`.
- Event-bus request/reply acknowledgement for accepted background jobs.
- Observable `DISPATCH_FAILED` terminal state and deterministic missing-consumer scenario.
- Packaged-JAR end-to-end verification shared by local quickstart and CI.
- Release workflow that publishes the executable JAR, SHA-256 checksum, and smoke-test transcript.

### Changed

- Workers claim jobs before reading the repository; duplicate commands and late progress cannot restart terminal work.
- A failed initial HTTP response write records `DISPATCH_FAILED` with `HTTP_RESPONSE_FAILED` and releases active capacity.
- Quickstart now runs the test suite before starting the packaged application.
- The demo polls the accepted job to completion before querying its result.

## 0.7.0 - 2026-07-02

### Added

- Spring Boot 4 and Vert.x 5 modernization.
- Concurrent HTTP fan-out with partial-failure isolation.
- Accepted worker-job flow, SSE lifecycle stream, and in-memory search result.
- Executable quickstart, architecture artwork, and animated event-loop hero.
