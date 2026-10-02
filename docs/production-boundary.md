# Production Boundary

The default profile runs every dependency in one process, keeping the handoffs visible. A fresh clone works offline after Maven resolves the dependencies. Several components are deliberate test doubles.

| Sample component | Production concern |
|---|---|
| Local inventory handlers | Real discovery, authentication, deadlines, rate limits, retries, and circuit policy |
| Vert.x local event bus | Durable broker or another delivery mechanism when process loss must not lose work |
| `BookJobRegistry` | Durable state and idempotency, shared admission across instances, cancellation, execution deadlines, and recovery |
| `BookSearchIndex` | External index or durable result store with publication and rollback rules |
| SSE monitor | Authenticated telemetry with bounded retention and sensitive-data controls |
| H2 | Production database sizing, migrations, credentials, backup, and failover |

Event-bus request/reply confirms receipt by a local consumer. It does not prove that the job will survive a crash or complete exactly once.

The sample bounds active work and retained records and deduplicates submissions within one process. Keys expire with terminal records and disappear on restart. A deployed service needs durable, tenant-scoped keys and an atomic relationship between registration and delivery. The default single active reindex job also prevents concurrent index publication; increasing that limit requires a policy for stale or out-of-order rebuilds.

These placement rules still apply when the infrastructure changes:

```text
non-blocking I/O -> keep it on the event loop and compose futures
blocking work    -> isolate it on workers or an external job system
```

Do not use the local elapsed time as a Spring-versus-Vert.x benchmark. The deterministic fixtures verify request structure, state transitions, failure visibility, and thread ownership.
