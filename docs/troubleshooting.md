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

Poll the `Location` returned by the POST request and inspect `status`, `terminal`, `failureCode`, and `error`. `DISPATCH_FAILED` means no worker acknowledgement arrived. `FAILED` means blocking work started and then failed.

Use the SSE stream to inspect the last completed handoff:

```bash
curl -N http://localhost:8989/book/events
```

## Full Demo Output

The default demo prints a compact verification transcript. Enable complete JSON responses with:

```bash
DEMO_VERBOSE=1 ./scripts/demo.sh
```
