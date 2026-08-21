# Contributing

This repository is a small, runnable lab for Vert.x and Spring Boot async boundaries. Contributions should keep the default path self-contained and easy to verify from a fresh clone.

## Before You Start

Open an issue for a new workload, dependency, or public API. Small bug fixes and documentation corrections can go straight to a pull request.

Good contributions usually do one of the following:

- Make a concurrency or thread-ownership guarantee executable.
- Add a deterministic failure scenario.
- Reduce the steps between cloning the repository and verifying the result.
- Clarify where the in-process sample stops being safe for production.

## Local Verification

Requirements: Java 17 or newer, Bash, and `curl`.

```bash
./mvnw verify -P h2local
./scripts/packaged-jar-smoke.sh
```

The smoke test must finish with the normal job in `COMPLETED`, the missing-consumer scenario in `DISPATCH_FAILED`, and a non-empty search result.

From a fresh clone, `./scripts/quickstart.sh` runs the same build, tests, packaged-JAR scenario, and cleanup.

## Pull Requests

- Keep each pull request focused on one behavior or documentation goal.
- Add tests for lifecycle transitions, failure paths, and concurrency guarantees affected by the change.
- Do not add an external database, broker, collector, or service to the default `h2local` path.
- Update the README or a linked document when a command, endpoint, state, or production limitation changes.
- Do not include generated build output from `target/`.

## Release Process

Maintainers prepare a final Maven version before creating a tag. `scripts/verify-release-version.sh` rejects snapshot versions and tags that do not match the POM. A valid `vX.Y.Z` tag runs the full test suite and packaged-JAR scenario before publishing the JAR, its SHA-256 checksum, and the smoke transcript.
