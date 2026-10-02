# Retries and Capacity

A client can lose the response to a POST even when the server has accepted the work. Send an `Idempotency-Key` when retrying should return the existing job. A key identifies one submission, including its `fail` option.

## Try a Retry

Start the application with `./mvnw spring-boot:run -P h2local`, then run:

```bash
curl -i -X POST http://localhost:8989/book/jobs/reindex \
  -H 'Idempotency-Key: catalog-refresh-001'

curl -i -X POST http://localhost:8989/book/jobs/reindex \
  -H 'Idempotency-Key: catalog-refresh-001'
```

The first response is `202 Accepted`. The second is `200 OK` with the same job ID and `Location`, plus `Idempotency-Replayed: true`. Its body contains the current status, even if the job has already completed or failed. It does not start another worker.

Keys are optional, case-sensitive, and limited to 1-128 ASCII letters, digits, dots, underscores, colons, or hyphens. An invalid or repeated header returns `400 Bad Request`. Without a key, each accepted request creates a new job.

Reusing a key with different options returns `409 Conflict`:

```bash
curl -i -X POST 'http://localhost:8989/book/jobs/reindex?fail=dispatch' \
  -H 'Idempotency-Key: catalog-refresh-001'
```

The response includes `data.failureCode: IDEMPOTENCY_KEY_CONFLICT`. To deliberately retry a failed job as new work, use a new key after inspecting the failure.

## Try the Capacity Limit

The default limit allows one active reindex job, counting accepted, dispatching, and running jobs together. It keeps rebuilds of the shared index from overlapping. A new submission over that limit returns `503 Service Unavailable`, `Retry-After: 1`, and `data.failureCode: JOB_CAPACITY_EXCEEDED`. It creates no job and has no `Location` header.

For a visible demonstration, stop any existing instance and increase the simulated blocking delay:

```bash
DEMO_REINDEX_ITEM_DELAY_MS=2000 ./mvnw spring-boot:run -P h2local
```

In another terminal, submit a job and immediately submit a different one:

```bash
curl -i -X POST http://localhost:8989/book/jobs/reindex \
  -H 'Idempotency-Key: slow-refresh-001'
curl -i -X POST http://localhost:8989/book/jobs/reindex \
  -H 'Idempotency-Key: slow-refresh-002'
```

While the first job is active, the second request returns `503`. Poll the first response's `Location` until `terminal` is `true`, then repeat the second request. It can now return `202`. Repeating the first key still returns the first job, including while capacity is full.

The delay makes the behavior easier to see; it is not a performance test. Automated HTTP tests verify rejection by holding a job open until the test releases it. They do not depend on timing assumptions. `Retry-After` is a retry hint, not a promise that capacity will be available one second later.

## Retention and Settings

Both profiles use these defaults. Override them through Spring properties or environment variables:

| Property | Environment variable | Default |
|---|---|---|
| `demo.jobs.max-active` | `DEMO_JOBS_MAX_ACTIVE` | `1` |
| `demo.jobs.max-retained` | `DEMO_JOBS_MAX_RETAINED` | `256` |
| `demo.jobs.retention-seconds` | `DEMO_JOBS_RETENTION_SECONDS` | `900` |

All limits must be positive, and `max-retained` must be at least `max-active`. Retained records include active jobs. When the record limit is reached, new work returns `503` with `data.failureCode: JOB_HISTORY_FULL`. Existing keys still replay; they are not evicted early to make room.

A terminal record and its key expire together 900 seconds after completion or failure by default. Admission and lookup remove expired records. Polling an expired job returns `404`, and submitting its old key can create a new job. Active jobs never expire. A stuck worker therefore holds its slot until it finishes, fails, or the process restarts; this sample has no cancellation or execution deadline.

Keep `max-active=1` for the shared index demo. Raising it permits overlapping rebuilds, whose results are published in completion order. The setting does not resize or reserve the Vert.x worker pool, and the admission limit applies only to reindex jobs, not CRUD or fan-out requests.

## What the Process Guarantees

Key lookup, capacity checks, and registration are atomic within this JVM. Repeated submissions cannot allocate two records for the same retained key. Workers claim a job before reading the repository, so a duplicate or late command cannot rebuild that job again. Completion and failure cannot be overwritten by late progress.

If writing the initial HTTP response fails, dispatch does not start. The job becomes `DISPATCH_FAILED` with `HTTP_RESPONSE_FAILED`, releasing active capacity. If the response write succeeds but its delivery to the client is uncertain, retrying the same key lets the client recover the job's current state.

The registry, keys, and results are in memory. Restarting loses them. Multiple application instances do not share them, and there is no durable delivery or exactly-once guarantee. See [Production Boundary](production-boundary.md) before adapting the pattern to a deployed service.

`./scripts/quickstart.sh` checks successful and failed job replay, key conflict, and the existing fan-out and search flows. `BookJobRegistryTest`, `BookJobHttpTest`, and `BookReindexJobWorkerTest` cover concurrent submissions, capacity, expiration, response-write failure, and duplicate delivery.
