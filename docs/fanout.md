# Concurrent HTTP Fan-Out

`GET /book/availability/{bookId}` calls three local inventory providers with deterministic behavior. `BookAvailabilityAggregator` starts every `WebClient` request before waiting for a response, then combines the futures.

```bash
curl http://localhost:8989/book/availability/1
```

The providers simulate 180, 320, and 240 milliseconds of latency. A typical request finishes near the slowest provider rather than the 740 millisecond sequential sum:

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

## Partial Failure

The `fail` query parameter makes one provider return `503` after its normal delay:

```bash
curl "http://localhost:8989/book/availability/1?fail=busan"
```

The aggregate response remains `200 OK`. It marks Busan as `UNAVAILABLE`, sets `partial` to `true`, and retains the two successful offers.

## Executable Guarantee

`BookAvailabilityAggregatorTest.startsEveryProviderRequestBeforeWaitingForAResponse` does not rely on a timing threshold. Its test server withholds every response until all three requests have arrived. A sequential implementation would time out, while the concurrent implementation completes.

The test provides structural evidence of concurrent request initiation. It is not a framework throughput benchmark, and the reported latency comes from local test doubles on the current machine.
