#!/usr/bin/env bash

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8989}"
BASE_URL="${BASE_URL%/}"
JOB_POLL_ATTEMPTS="${JOB_POLL_ATTEMPTS:-80}"
JOB_POLL_INTERVAL="${JOB_POLL_INTERVAL:-0.25}"
DEMO_VERBOSE="${DEMO_VERBOSE:-0}"

if ! command -v curl >/dev/null 2>&1; then
  printf 'curl is required to run this demo.\n' >&2
  exit 1
fi

if ! curl --fail --silent "${BASE_URL}/" >/dev/null; then
  printf 'The app is not ready at %s. Start it with: ./mvnw spring-boot:run -P h2local\n' "${BASE_URL}" >&2
  exit 1
fi

tmp_dir=$(mktemp -d "${TMPDIR:-/tmp}/vertx-spring-demo.XXXXXX")
sse_pid=''

cleanup() {
  if [[ -n "${sse_pid}" ]] && kill -0 "${sse_pid}" 2>/dev/null; then
    kill "${sse_pid}" 2>/dev/null || true
    wait "${sse_pid}" 2>/dev/null || true
  fi
  rm -rf "${tmp_dir}"
}
trap cleanup EXIT

show_file() {
  if [[ "${DEMO_VERBOSE}" == "1" ]]; then
    sed -n "${2:-1,180p}" "$1"
  fi
}

json_value() {
  local file="$1"
  local field="$2"
  awk -v key="\"${field}\"" '
    index($0, key) {
      value = $0
      sub(/.*:[[:space:]]*/, "", value)
      sub(/,[[:space:]]*$/, "", value)
      gsub(/\"/, "", value)
      print value
      exit
    }
  ' "${file}"
}

stop_sse() {
  if [[ -z "${sse_pid}" ]]; then
    return
  fi
  if kill -0 "${sse_pid}" 2>/dev/null; then
    kill "${sse_pid}" 2>/dev/null || true
  fi
  wait "${sse_pid}" 2>/dev/null || true
  sse_pid=''
}

require_event() {
  if ! grep -q "^event: $1$" "${tmp_dir}/events.txt"; then
    printf 'Expected SSE phase %s was not observed.\n' "$1" >&2
    exit 1
  fi
}

print_boundary_trace() {
  printf '\nObserved boundary trace:\n'
  awk '
    /^event: / { phase = $2; next }
    /^data: / {
      include = phase ~ /^(io\.fanout\.(started|completed)|event-loop\.dispatch|job\.(accepted|dispatching|dispatched|started|completed|dispatch_failed))$/
      if (!include && !(phase == "event-loop.completed" && $0 ~ /"statusCode":202/)) {
        next
      }
      thread = $0
      sub(/.*"thread":"/, "", thread)
      sub(/".*/, "", thread)
      printf "  %-24s %s\n", phase, thread
    }
  ' "${tmp_dir}/events.txt"
}

job_status() {
  awk -F '"' '/"status"[[:space:]]*:/ { print $4; exit }' "$1"
}

submit_and_wait() {
  local key="$1"
  local endpoint="$2"
  local expected_status="$3"
  local headers="${tmp_dir}/${key}-headers.txt"
  local response="${tmp_dir}/${key}-accepted.json"
  local polled="${tmp_dir}/${key}-status.json"
  local location=''
  local status=''
  local previous_status=''

  curl --fail --silent --show-error \
    --dump-header "${headers}" \
    --output "${response}" \
    --request POST "${BASE_URL}${endpoint}"

  location=$(awk 'tolower($1) == "location:" { gsub("\\r", "", $2); print $2; exit }' "${headers}")
  if [[ -z "${location}" ]]; then
    printf 'The accepted response did not include a Location header.\n' >&2
    sed -n '1,80p' "${headers}" >&2
    sed -n '1,120p' "${response}" >&2
    exit 1
  fi

  printf 'accepted -> %s\n' "${location}"
  if [[ "${DEMO_VERBOSE}" == "1" ]]; then
    sed -n '/^HTTP\//p; /^[Ll]ocation:/p; /^[Rr]etry-[Aa]fter:/p' "${headers}"
  fi
  show_file "${response}" '1,120p'

  for ((attempt = 1; attempt <= JOB_POLL_ATTEMPTS; attempt++)); do
    curl --fail --silent --show-error --output "${polled}" "${BASE_URL}${location}"
    status=$(job_status "${polled}")
    if [[ -z "${status}" ]]; then
      printf 'The job status response did not contain a status field.\n' >&2
      sed -n '1,160p' "${polled}" >&2
      exit 1
    fi
    if [[ "${status}" != "${previous_status}" ]]; then
      printf 'job %s -> %s\n' "${location##*/}" "${status}"
      previous_status="${status}"
    fi

    case "${status}" in
      COMPLETED|FAILED|DISPATCH_FAILED)
        show_file "${polled}"
        if [[ "${status}" != "${expected_status}" ]]; then
          printf 'Expected job status %s but received %s.\n' "${expected_status}" "${status}" >&2
          exit 1
        fi
        printf '[ok] Job reached expected terminal status %s.\n' "${status}"
        return 0
        ;;
    esac
    sleep "${JOB_POLL_INTERVAL}"
  done

  printf 'Job %s did not reach a terminal state after %s polls.\n' \
    "${location##*/}" "${JOB_POLL_ATTEMPTS}" >&2
  sed -n '1,180p' "${polled}" >&2
  exit 1
}

printf '\nWatching event-loop I/O, dispatch, and worker lifecycle events...\n\n'
curl --no-buffer --silent --show-error --max-time 30 \
  "${BASE_URL}/book/events" >"${tmp_dir}/events.txt" &
sse_pid=$!

sleep 0.4
printf '\nQuerying three inventory services concurrently...\n\n'
curl --fail --silent --show-error --output "${tmp_dir}/fanout.json" \
  "${BASE_URL}/book/availability/1"
show_file "${tmp_dir}/fanout.json"
if ! grep -q '"partial" : false' "${tmp_dir}/fanout.json"; then
  printf 'The healthy fan-out did not return a complete result.\n' >&2
  exit 1
fi
printf '[ok] Healthy fan-out returned %s providers in %s ms (%s ms sequential simulation).\n' \
  "$(json_value "${tmp_dir}/fanout.json" providerCount)" \
  "$(json_value "${tmp_dir}/fanout.json" elapsedMs)" \
  "$(json_value "${tmp_dir}/fanout.json" simulatedSequentialLatencyMs)"

printf '\nRepeating the fan-out with one simulated downstream failure...\n\n'
curl --fail --silent --show-error --output "${tmp_dir}/partial.json" \
  "${BASE_URL}/book/availability/1?fail=busan"
show_file "${tmp_dir}/partial.json"
if ! grep -q '"partial" : true' "${tmp_dir}/partial.json"; then
  printf 'The failed-provider scenario did not return a partial result.\n' >&2
  exit 1
fi
printf '[ok] Partial failure kept %s responses and marked %s provider unavailable.\n' \
  "$(json_value "${tmp_dir}/partial.json" respondedProviders)" \
  "$(json_value "${tmp_dir}/partial.json" unavailableProviders)"

printf '\nSubmitting a search-index rebuild and polling its Location...\n\n'
submit_and_wait normal '/book/jobs/reindex' COMPLETED

printf '\nSearching the index published by the completed worker...\n\n'
curl --fail --silent --show-error --output "${tmp_dir}/search.json" \
  "${BASE_URL}/book/search?q=Hyeon-Sang"
show_file "${tmp_dir}/search.json"
match_count=$(awk -F '[: ,]+' '/"matchCount"[[:space:]]*:/ { print $3; exit }' "${tmp_dir}/search.json")
if [[ ! "${match_count}" =~ ^[0-9]+$ ]] || (( match_count < 1 )); then
  printf 'The completed rebuild did not publish a searchable document.\n' >&2
  exit 1
fi
printf '[ok] Search observed %s indexed match(es).\n' "${match_count}"

printf '\nSubmitting to a missing consumer to prove dispatch failure is observable...\n\n'
submit_and_wait dispatch '/book/jobs/reindex?fail=dispatch' DISPATCH_FAILED

sleep 0.2
stop_sse

for phase in \
  io.fanout.started \
  io.fanout.completed \
  event-loop.dispatch \
  job.accepted \
  job.dispatching \
  job.dispatched \
  job.started \
  job.completed \
  job.dispatch_failed; do
  require_event "${phase}"
done

print_boundary_trace
printf '\n[ok] Deterministic async boundary demo passed.\n'
