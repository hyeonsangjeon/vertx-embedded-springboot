# Vert.x + Spring Boot: Event Loop and Async Worker Patterns

[![CI](https://github.com/hyeonsangjeon/vertx-embedded-springboot/actions/workflows/ci.yml/badge.svg)](https://github.com/hyeonsangjeon/vertx-embedded-springboot/actions/workflows/ci.yml)
![Java 17](https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white)
![Vert.x 5.1.6](https://img.shields.io/badge/Vert.x-5.1.6-782A90?logo=eclipse-vert.x&logoColor=white)
![Spring Boot 4.1.0](https://img.shields.io/badge/Spring%20Boot-4.1.0-6DB33F?logo=springboot&logoColor=white)
![Maven](https://img.shields.io/badge/build-Maven-C71A36?logo=apachemaven&logoColor=white)
![License](https://img.shields.io/badge/license-Apache--2.0-blue)

This repository is a runnable reference for two asynchronous service boundaries:

| Workload | Vert.x pattern |
|---|---|
| Several independent HTTP calls | Fan out on the event loop with `WebClient`, then combine the futures |
| JDBC or another blocking SDK | Return `202 Accepted`, dispatch over the event bus, and run on a worker thread |

![Animated Vert.x event loop and worker flow](docs/event-loop-hero.gif)

The examples use a book catalog, but the same boundaries apply to inventory aggregation, dataset ingestion, model refresh, report generation, media conversion, and bulk synchronization. Spring Boot owns application lifecycle and persistence. Embedded Vert.x owns the public HTTP boundary, event bus, non-blocking clients, worker pool, and live event stream.

Modernized with **OpenAI Codex** as an AI coding collaborator. See [CONTRIBUTORS.md](CONTRIBUTORS.md).

## Run the Whole Demo

Requirements: Java 17 or newer, `curl`, and Bash (macOS, Linux, WSL, or Git Bash).

```bash
git clone https://github.com/hyeonsangjeon/vertx-embedded-springboot.git
cd vertx-embedded-springboot
./scripts/quickstart.sh
```

That one command builds the executable jar, starts the H2-backed application, runs both examples, and shuts the process down. The Maven Wrapper downloads Maven 3.9.16 on first use. On macOS, the script also selects an installed Java 17 runtime when the shell still defaults to an older JDK.

No external database, broker, search engine, downstream service, or `jq` installation is required.

From Windows PowerShell, start the persistent application with `.\mvnw.cmd spring-boot:run -P h2local`, then run the documented `curl` calls directly.

To keep the application running for exploration, use two terminals instead:

```bash
# Terminal 1
./mvnw spring-boot:run -P h2local
```

```bash
# Terminal 2
./scripts/demo.sh
```

The public API is available at [http://localhost:8989](http://localhost:8989). Its root response lists the main calls and the event-loop thread that served the request.

## Example 1: Concurrent MSA Fan-Out

An availability request calls three independent inventory services over real local HTTP connections:

```bash
curl http://localhost:8989/book/availability/1
```

The providers simulate latencies of 180, 320, and 240 milliseconds. `BookAvailabilityAggregator` starts all three requests before waiting for any response, then combines them with `Future.all`. Normal elapsed time is therefore close to the slowest call rather than the 740 millisecond sequential total.

A typical response, with the per-provider metadata shortened here, looks like this:

```json
{
  "statusCode": 200,
  "data": {
    "bookId": 1,
    "strategy": "concurrent-http-fan-out",
    "providerCount": 3,
    "respondedProviders": 3,
    "unavailableProviders": 0,
    "totalQuantity": 8,
    "partial": false,
    "simulatedSequentialLatencyMs": 740,
    "elapsedMs": 329,
    "offers": [
      { "provider": "seoul", "status": "AVAILABLE", "quantity": 3 },
      { "provider": "busan", "status": "OUT_OF_STOCK", "quantity": 0 },
      { "provider": "incheon", "status": "AVAILABLE", "quantity": 5 }
    ]
  }
}
```

The aggregator isolates downstream failures. Use the built-in fault switch to see a partial result:

```bash
curl "http://localhost:8989/book/availability/1?fail=busan"
```

The response remains `200 OK`, marks Busan as `UNAVAILABLE`, and keeps the successful offers. No worker thread is involved because the entire flow is non-blocking HTTP I/O.

## Example 2: Accepted Background Work

The second flow rebuilds an in-memory book search index. The request returns before any blocking database or indexing work begins:

```text
HTTP event loop -> 202 Accepted -> event bus -> worker thread -> observable result
```

Submit the job:

```bash
curl -i -X POST http://localhost:8989/book/jobs/reindex
```

The response includes a job ID and polling location:

```http
HTTP/1.1 202 Accepted
Location: /book/jobs/f97f7c86-0581-47b5-bef4-1045f218bb69
Retry-After: 1
Content-Type: application/json; charset=utf-8
```

```json
{
  "statusCode": 202,
  "data": {
    "jobId": "f97f7c86-0581-47b5-bef4-1045f218bb69",
    "type": "book.search-index.rebuild",
    "status": "ACCEPTED",
    "progressPercent": 0,
    "links": {
      "status": "/book/jobs/f97f7c86-0581-47b5-bef4-1045f218bb69",
      "events": "/book/events",
      "search": "/book/search?q=Hyeon-Sang"
    }
  },
  "message": "search index rebuild accepted"
}
```

Poll the `Location` URL, then query the published index:

```bash
curl http://localhost:8989/book/jobs/{jobId}
curl "http://localhost:8989/book/search?q=Hyeon-Sang"
```

The index is replaced as one immutable snapshot. Readers see either the previous complete index or the new one, never a partially rebuilt state.

## Watch the Handoffs

Open the Server-Sent Events stream before making either request:

```bash
curl -N http://localhost:8989/book/events
```

The non-blocking HTTP flow emits:

```text
event-loop.received
io.fanout.started
io.fanout.completed
event-loop.completed
```

The accepted worker flow is deliberately ordered:

```text
event-loop.received
job.accepted
event-loop.completed      <- the HTTP 202 response has been written
event-loop.dispatch
job.started               <- now running on vert.x-worker-thread-*
job.progress
job.completed
```

Each event includes a sequence number, request ID, operation, thread, and elapsed time. This makes event-loop ownership and worker handoff visible without attaching a debugger.

## Architecture

![Vert.x async worker architecture](docs/async-worker-pattern.svg)

The application has three request shapes:

| Shape | Flow |
|---|---|
| Concurrent I/O aggregation | Event loop -> WebClient calls -> `Future.all` -> partial-safe HTTP response |
| Request/response persistence | Event loop -> service proxy -> worker -> JPA/MyBatis -> event-bus reply -> HTTP response |
| Accepted background job | Event loop -> HTTP 202 -> event-bus command -> worker -> job status, SSE, and search index |

Worker consumers are deployed before the HTTP facade. The server cannot accept traffic before its event-bus handlers exist.

## API

Vert.x serves the public API at `http://localhost:8989`.

| Method | Path | Description |
|---|---|---|
| `GET` | `/` | Discover the sample flows and useful endpoints |
| `GET` | `/book/availability/{bookId}` | Query three inventory services concurrently |
| `GET` | `/book/availability/{bookId}?fail=busan` | Simulate one failed downstream call |
| `GET` | `/book/list` | List books through the worker service proxy |
| `GET` | `/book/id/{bookId}` | Read one book with MyBatis |
| `POST` | `/book/add` | Create a book with Spring Data JPA |
| `PUT` | `/book/update` | Update a book |
| `DELETE` | `/book/delete/{bookId}` | Delete a book |
| `GET` | `/book/events` | Stream event-loop, I/O, worker, and job phases over SSE |
| `POST` | `/book/jobs/reindex` | Accept a search-index rebuild job |
| `GET` | `/book/jobs/{jobId}` | Read job status and progress |
| `GET` | `/book/search?q={query}` | Search the index published by the job |

Create a book:

```bash
curl -X POST http://localhost:8989/book/add \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Designing Event-Driven Systems",
    "author": "Example Author",
    "pages": 320
  }'
```

## Code Map

| Component | Responsibility |
|---|---|
| `Application` | Creates Vert.x and deploys workers before the HTTP facade |
| `VertxFacade` | Owns the event-loop HTTP boundary and discovery response |
| `BookAvailabilityAggregator` | Fans out concurrent WebClient calls and preserves partial results |
| `DemoInventoryProviderHandler` | Supplies deterministic local downstream HTTP behavior |
| `RouteHandler` / `RequestHandler` | Validate requests, compose futures, return 202, and format responses |
| `BookAsyncService` | Defines the Vert.x 5 Future-based service proxy contract |
| `VertxWorker` | Registers persistence and job consumers on the worker pool |
| `BookReindexJobWorker` | Performs the blocking search-index rebuild |
| `BookJobRegistry` | Stores accepted-job state for this self-contained sample |
| `BookSearchIndex` | Atomically publishes and queries the rebuilt index snapshot |
| `EventLoopMonitor` | Publishes request, I/O, worker, and job phases to SSE clients |

## Ports and Configuration

| Port | Service | Useful URL |
|---|---|---|
| `8989` | Vert.x public HTTP server | `http://localhost:8989/` |
| `7979` | Spring Actuator | `http://localhost:7979/actuator/health` |
| `9000` | Spring MVC and H2 console | `http://localhost:9000/h2-console` |

Profile-specific settings live under `src/main/resources/profiles/{profile}/`.

| Profile | Purpose |
|---|---|
| `h2local` | Zero-setup local run with an in-memory H2 database |
| `mariadb` | External MariaDB-compatible deployment example |

```properties
vertx.port=8989
vertx.worker.pool.size=6
vertx.springWorker.instances=4
vertx.max.eventloop.execute.time=10000
vertx.blocked.thread.check.interval=1000
demo.reindex.item-delay-ms=150
```

The reindex delay stands in for a blocking Elasticsearch, OpenSearch, vector-store, model-registry, or filesystem SDK call. It stays inside the worker implementation so the event loop remains responsive.

For MariaDB, update `src/main/resources/profiles/mariadb/application.properties`, then run:

```bash
./mvnw spring-boot:run -P mariadb
```

## Development

Run the full verification suite:

```bash
./mvnw verify -P h2local
```

Build and run the executable jar:

```bash
./mvnw clean package -P h2local
java -jar target/vertx-embedded-springboot-0.8.0-SNAPSHOT.jar
```

Regenerate the animated hero from its SVG source:

```bash
node scripts/render-event-loop-hero-gif.mjs
```

The GitHub social preview is available at `docs/social-preview.png`; its editable source is `docs/social-preview.svg`.

## Production Boundary

The sample keeps infrastructure in process so each handoff is easy to inspect. The local inventory providers are test doubles. `BookJobRegistry`, `BookSearchIndex`, and the Vert.x event bus are not durable across restarts.

A production implementation should use real service discovery, authentication, deadlines, and circuit breaking for downstream calls. Background jobs should persist state, define idempotency and retry rules, use a durable broker when delivery guarantees matter, and publish results to external storage.

The core decisions remain the same:

```text
non-blocking I/O -> stay on the event loop and compose futures
blocking work    -> accept or proxy, then isolate it on workers
```

## Stack

- Java 17
- Vert.x 5.1.6
- Spring Boot 4.1.0
- MyBatis Spring Boot Starter 4.1.0
- Spring Data JPA and Liquibase
- H2 and MariaDB-compatible JDBC
- Maven Wrapper 3.9.16

## Contributors

Built and maintained by Hyeonsang Jeon, with OpenAI Codex acknowledged as an AI coding collaborator for the modernization work. See [CONTRIBUTORS.md](CONTRIBUTORS.md).

## References

- [Vert.x Core Documentation](https://vertx.io/docs/vertx-core/java/)
- [Vert.x Web Client](https://vertx.io/docs/vertx-web-client/java/)
- [Vert.x Service Proxies](https://vertx.io/docs/vertx-service-proxy/java/)
- [Vert.x 4 to 5 Migration Guide](https://vertx.io/docs/guides/vertx-5-migration-guide/)
- [Spring Boot Documentation](https://docs.spring.io/spring-boot/)

## License

Licensed under the [Apache License 2.0](LICENSE).
