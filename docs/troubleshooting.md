# Troubleshooting

## Java Is Older Than 17

`quickstart.sh` selects an installed Java 17 runtime on macOS. In other environments, set `JAVA_HOME` before running Maven:

```bash
java -version
export JAVA_HOME=/path/to/jdk-17
./scripts/quickstart.sh
```

## Port 8989 Is Already in Use

Quickstart refuses to reuse an existing process because it must verify the JAR it just built. Stop that process, run `./scripts/demo.sh` if the existing process is intentional, or move both the server and verifier:

```bash
VERTX_PORT=8990 BASE_URL=http://localhost:8990 ./scripts/quickstart.sh
```

## The Application Does Not Become Ready

Run the application in the foreground to see the full startup log:

```bash
./mvnw spring-boot:run -P h2local
```

Check that ports `8989`, `7979`, and `9000` are free. The public Vert.x server starts only after the worker consumers register.

## The Job Never Completes

Poll the `Location` returned by the POST request and inspect `status`, `terminal`, `failureCode`, and `error`. `DISPATCH_FAILED` means dispatch failed or the initial HTTP response could not be written. Its failure code distinguishes `EVENT_BUS_DISPATCH_FAILED` from `HTTP_RESPONSE_FAILED`. `FAILED` means blocking work started and then failed.

Use the SSE stream to inspect the last completed handoff:

```bash
curl -N http://localhost:8989/book/events
```

## A Submission Returns 503 or 409

Read `data.failureCode`. `JOB_CAPACITY_EXCEEDED` means another job occupies the active limit; wait for it to finish before retrying. `JOB_HISTORY_FULL` means retained records have filled the registry; wait for terminal records to expire. Both responses include `Retry-After`, and existing keys still replay.

`IDEMPOTENCY_KEY_CONFLICT` means the same key was used with different options. Reuse the original options to inspect that job, or choose a new key for a new submission. [Retries and Capacity](job-admission.md) lists the settings and examples.

## A Job Returns 404

Job records and keys are in memory. They disappear on restart, or expire 15 minutes after reaching a terminal state with the default settings. Active jobs do not expire. Reusing an expired key can create new work.

## Full Demo Output

The default demo prints a compact verification transcript. Enable complete JSON responses with:

```bash
DEMO_VERBOSE=1 ./scripts/demo.sh
```
