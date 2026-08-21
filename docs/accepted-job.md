# Accepted Background Job

`POST /book/jobs/reindex` accepts a request to rebuild the in-memory search index. The HTTP response is written before event-bus dispatch starts.

```text
ACCEPTED -> DISPATCHING -> RUNNING -> COMPLETED
              |              |
              |              +----> FAILED
              +-------------------> DISPATCH_FAILED
```

Every status response includes `terminal`. `COMPLETED`, `FAILED`, and `DISPATCH_FAILED` are terminal states.

## Submit and Poll

```bash
curl -i -X POST http://localhost:8989/book/jobs/reindex
```

```http
HTTP/1.1 202 Accepted
Location: /book/jobs/f97f7c86-0581-47b5-bef4-1045f218bb69
Retry-After: 1
```

```json
{
  "statusCode": 202,
  "data": {
    "jobId": "f97f7c86-0581-47b5-bef4-1045f218bb69",
    "type": "book.search-index.rebuild",
    "status": "ACCEPTED",
    "terminal": false,
    "progressPercent": 0,
    "links": {
      "status": "/book/jobs/f97f7c86-0581-47b5-bef4-1045f218bb69",
      "events": "/book/events",
      "search": "/book/search?q=Hyeon-Sang",
      "dispatchFailure": "/book/jobs/reindex?fail=dispatch"
    }
  }
}
```

Poll the `Location` until `terminal` is `true`, then read the published result:

```bash
curl http://localhost:8989/book/jobs/{jobId}
curl "http://localhost:8989/book/search?q=Hyeon-Sang"
```

The worker builds a replacement document list and publishes it as one immutable snapshot. Readers see either the old complete index or the new one, never a partially rebuilt index.

## Dispatch Acknowledgement

After the `202` write completes, the facade sends an event-bus request with a two-second reply timeout. A worker consumer emits `job.dispatched` and replies before it begins the blocking repository and indexing work.

The failure scenario targets an address with no consumer:

```bash
curl -X POST "http://localhost:8989/book/jobs/reindex?fail=dispatch"
```

Polling its `Location` returns `DISPATCH_FAILED` with the failure code `EVENT_BUS_DISPATCH_FAILED`. When local dispatch fails, the job cannot remain accepted indefinitely. It moves to a terminal state.

Request/reply proves that an in-process consumer received the command. It does not provide persistence, retry, idempotency, cross-instance coordination, or recovery after a process restart.

## Observable Order

```text
event-loop.received
job.accepted
event-loop.completed
event-loop.dispatch
job.dispatching
job.dispatched
job.started
job.progress
job.completed
```

The `job.dispatched` and `job.started` events, along with progress and completion, run on `vert.x-worker-thread-*`. Acceptance, HTTP completion, and dispatch failure run on the event-loop thread.
