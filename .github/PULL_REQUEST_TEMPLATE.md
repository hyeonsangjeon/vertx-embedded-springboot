## What changed

Describe the behavior or documentation change.

## Why

Explain which async boundary, failure mode, or first-run problem this addresses.

## Verification

- [ ] `./mvnw verify -P h2local`
- [ ] `./scripts/packaged-jar-smoke.sh` when runtime behavior changed
- [ ] Documentation reflects changed commands, endpoints, states, and limitations
