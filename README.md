# Vert.x Embedded Spring Boot Async Worker Pattern

[![CI](https://github.com/hyeonsangjeon/vertx-embedded-springboot/actions/workflows/ci.yml/badge.svg)](https://github.com/hyeonsangjeon/vertx-embedded-springboot/actions/workflows/ci.yml)
![Java 17](https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white)
![Vert.x 4.5.27](https://img.shields.io/badge/Vert.x-4.5.27-782A90?logo=eclipse-vert.x&logoColor=white)
![Spring Boot 4.1.0](https://img.shields.io/badge/Spring%20Boot-4.1.0-6DB33F?logo=springboot&logoColor=white)
![Maven](https://img.shields.io/badge/build-Maven-C71A36?logo=apachemaven&logoColor=white)
![License](https://img.shields.io/badge/license-Apache--2.0-blue)

> Event-loop based async worker pattern for long-running ML/platform jobs, implemented with Vert.x embedded in Spring Boot.

![Vert.x event loop animated hero](docs/event-loop-hero.gif)

This repository is a compact reference implementation of a practical async boundary:

```text
receive fast -> dispatch asynchronously -> execute off the event loop -> expose progress
```

It is not a microservice showcase. The important idea is smaller and more durable: keep HTTP event-loop work lightweight, move blocking or long-running work to worker threads, and make progress observable without making the caller wait.

## Highlights

- **Event-loop handoff**: Vert.x event-loop threads accept, route, trace, and return quickly.
- **Worker isolation**: Worker verticles run blocking or long-running platform work.
- **Event bus dispatch**: Service proxy calls and direct job commands move work off the HTTP path.
- **Accepted job pattern**: `POST /book/jobs/reindex` returns `202 Accepted` immediately.
- **Live observability**: `/book/events` streams event-loop, worker, and job lifecycle events over SSE.
- **Spring integration**: Verticles are Spring-managed components with dependency injection.
- **Persistence examples**: Spring Data JPA and MyBatis examples run against H2 or MariaDB.

## 60-Second Demo

Start the app:

```bash
mvn clean spring-boot:run -P h2local
```

Open the event stream:

```bash
curl -N http://localhost:8989/book/events
```

Submit work from another terminal:

```bash
curl -i -X POST http://localhost:8989/book/jobs/reindex
```

What to look for:

| Signal | Meaning |
|--------|---------|
| `HTTP/1.1 202 Accepted` | The event loop accepted the job and released the caller |
| `event-loop.completed` before `job.started` | The HTTP path finished before the long-running work began |
| `job.progress` on `vert.x-worker-thread-*` | Blocking work is isolated on the worker pool |
| `job.completed` | The background job finished and status is observable |

## Pattern

![Vert.x async worker pattern](docs/async-worker-pattern.svg)

The event loop owns the request boundary. It should not own long-running work.

For normal service calls, the result returns through the event bus after a worker finishes the service method. For accepted jobs, the HTTP response returns first, then the job continues on a worker thread while status and SSE events expose progress.

**Core components**

| Component | Role |
|-----------|------|
| `VertxFacade` | HTTP/event-loop boundary for routing, tracing, and fast acceptance |
| `RouteHandler` / `RequestHandler` | Web routes, validation, event-bus dispatch, response formatting |
| `VertxWorker` | Worker-pool verticle that consumes service calls and job commands |
| `BookAsyncService` | Vert.x service proxy API for CRUD-style work |
| `BookReindexJobWorker` | Sample long-running background job worker |
| `BookJobRegistry` | In-memory job state store for the accepted-job sample |
| `EventLoopMonitor` | SSE trace publisher for event-loop, worker, and job lifecycle phases |

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.6.3+

If your machine defaults to Java 8, select Java 17 before running Maven:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

Run with the in-memory H2 profile:

```bash
mvn clean spring-boot:run -P h2local
```

The app starts:

| Port | Service | Description |
|------|---------|-------------|
| `8989` | Vert.x HTTP server | Book API and SSE stream |
| `7979` | Spring Actuator | Health, metrics, Liquibase details |
| `9000` | Spring Boot | Internal application port |

Watch for `SpringWorker started` and `Vert.x HTTP server started on port 8989`.

## Accepted Job Details

The background job endpoint:

```bash
curl -i -X POST http://localhost:8989/book/jobs/reindex
```

returns immediately:

```json
{
  "statusCode": 202,
  "data": {
    "jobId": "f97f7c86-0581-47b5-bef4-1045f218bb69",
    "type": "book.reindex",
    "status": "ACCEPTED",
    "total": 0,
    "processed": 0,
    "message": "job accepted"
  },
  "message": "job accepted"
}
```

Meanwhile, `/book/events` continues with the lifecycle:

```text
event: event-loop.received
event: job.accepted
event: event-loop.dispatch
event: event-loop.completed
event: job.started
event: job.progress
event: job.completed
```

Check job status:

```bash
curl http://localhost:8989/book/jobs/{jobId}
```

## API

All endpoints are served on `http://localhost:8989/book`.

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/list` | List all books |
| `GET` | `/id/{bookId}` | Get one book |
| `POST` | `/add` | Create a book |
| `PUT` | `/update` | Update a book |
| `DELETE` | `/delete/{bookId}` | Delete a book |
| `GET` | `/events` | SSE event-loop and worker trace |
| `POST` | `/jobs/reindex` | Accept a background reindex job |
| `GET` | `/jobs/{jobId}` | Read background job status |

Create a book:

```bash
curl -X POST http://localhost:8989/book/add \
  -H "Content-Type: application/json" \
  -d '{
    "name": "marble comics",
    "author": "marble",
    "pages": 987
  }'
```

Update a book:

```bash
curl -X PUT http://localhost:8989/book/update \
  -H "Content-Type: application/json" \
  -d '{
    "id": 1,
    "name": "updated title",
    "author": "new author",
    "pages": 500
  }'
```

Responses follow this shape:

```json
{
  "statusCode": 200,
  "data": {},
  "message": "operation success"
}
```

## Monitoring

Actuator endpoints:

```bash
curl http://localhost:7979/actuator
curl http://localhost:7979/actuator/liquibase
```

SSE trace:

```bash
curl -N http://localhost:8989/book/events
```

Each SSE event includes fields such as `requestId`, `operation`, `step`, `thread`, and `elapsedMs`, making the event-loop handoff visible without attaching a debugger.

## Configuration

Profile-specific configuration lives under `src/main/resources/profiles/{profile}/`.

| Profile | Description |
|---------|-------------|
| `h2local` | In-memory H2 database for local runs |
| `mariadb` | MariaDB-backed run profile |

Key Vert.x settings:

```properties
vertx.port=8989
vertx.worker.pool.size=6
vertx.springWorker.instances=4
vertx.max.eventloop.execute.time=10000
vertx.blocked.thread.check.interval=1000
```

For MariaDB, update `src/main/resources/profiles/mariadb/application.properties`, then run:

```bash
mvn clean spring-boot:run -P mariadb
```

## Development

Run tests:

```bash
mvn test -P h2local
```

Build the jar:

```bash
mvn clean package
```

Regenerate the animated README hero:

```bash
node scripts/render-event-loop-hero-gif.mjs
```

The GIF is generated from `docs/event-loop-hero.svg` into `docs/event-loop-hero.gif`.

GitHub social preview image:

```text
docs/social-preview.png
```

The editable source is `docs/social-preview.svg`. Upload the PNG in the GitHub repository settings under social preview.

## Stack

- **Java 17**
- **Vert.x 4.5.27**
- **Spring Boot 4.1.0**
- **Spring Data JPA**
- **MyBatis Spring Boot 4.0.1**
- **Liquibase**
- **H2 / MariaDB**
- **log4jdbc**

## Notes

- `BookJobRegistry` is intentionally in-memory for this sample. A production job runner should persist job state in a database or external store.
- The reindex job simulates long-running work so the event-loop handoff and worker progress are easy to observe.
- The repository keeps the pattern small on purpose: the goal is to show the async boundary clearly.

## References

- [Vert.x Core Documentation](https://vertx.io/docs/vertx-core/java/)
- [Vert.x Service Proxies](https://vertx.io/docs/vertx-service-proxy/java/)
- [Spring Boot Documentation](https://docs.spring.io/spring-boot/)

## License

Licensed under the [Apache License 2.0](LICENSE).
