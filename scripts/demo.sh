#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8989}"

if ! command -v curl >/dev/null 2>&1; then
  printf 'curl is required to run this demo.\n' >&2
  exit 1
fi

if ! curl --fail --silent "${BASE_URL}/" >/dev/null; then
  printf 'The app is not ready at %s. Start it with: ./mvnw spring-boot:run -P h2local\n' "${BASE_URL}" >&2
  exit 1
fi

printf '\nWatching the event loop and worker lifecycle...\n\n'
curl --no-buffer --silent --max-time 4 "${BASE_URL}/book/events" &
sse_pid=$!

cleanup() {
  if kill -0 "${sse_pid}" 2>/dev/null; then
    kill "${sse_pid}" 2>/dev/null || true
  fi
}
trap cleanup EXIT

sleep 0.4
printf '\nSubmitting a search-index rebuild...\n\n'
curl --silent --show-error --include --request POST "${BASE_URL}/book/jobs/reindex"
printf '\n'

wait "${sse_pid}" || true
trap - EXIT

printf '\nSearching the index built by the worker...\n\n'
curl --silent --show-error "${BASE_URL}/book/search?q=Hyeon-Sang"
printf '\n'
