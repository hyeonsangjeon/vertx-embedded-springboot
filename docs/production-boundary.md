# Production Boundary

The default profile runs every dependency in one process, keeping the handoffs visible. A fresh clone works offline after Maven resolves the dependencies. Several components are deliberate test doubles.

| Sample component | Production concern |
|---|---|
| Local inventory handlers | Real discovery, authentication, deadlines, rate limits, retries, and circuit policy |
| Vert.x local event bus | Durable broker or another delivery mechanism when process loss must not lose work |
| `BookJobRegistry` | Persistent state, retention, admission control, idempotency, cancellation, and recovery |
| `BookSearchIndex` | External index or durable result store with publication and rollback rules |
| SSE monitor | Authenticated telemetry with bounded retention and sensitive-data controls |
| H2 | Production database sizing, migrations, credentials, backup, and failover |

Event-bus request/reply confirms receipt by a local consumer. It does not prove that the job will survive a crash or complete exactly once.

These placement rules still apply when the infrastructure changes:

```text
non-blocking I/O -> keep it on the event loop and compose futures
blocking work    -> isolate it on workers or an external job system
```

Do not use the local elapsed time as a Spring-versus-Vert.x benchmark. The deterministic fixtures verify request structure, state transitions, failure visibility, and thread ownership.
