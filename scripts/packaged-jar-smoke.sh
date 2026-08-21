#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
BASE_URL="${BASE_URL:-http://localhost:8989}"
BASE_URL="${BASE_URL%/}"

cd "${ROOT_DIR}"

if ! command -v curl >/dev/null 2>&1; then
  printf 'curl is required to run the packaged-jar smoke test.\n' >&2
  exit 1
fi

if curl --fail --silent "${BASE_URL}/" >/dev/null 2>&1; then
  printf 'Cannot verify the packaged jar because %s is already in use.\n' "${BASE_URL}" >&2
  exit 1
fi

app_jar="${APP_JAR:-}"
if [[ -z "${app_jar}" ]]; then
  app_jar=$(find target -maxdepth 1 -type f \
    -name 'vertx-embedded-springboot-*.jar' \
    ! -name '*.original' \
    ! -name '*-sources.jar' \
    ! -name '*-javadoc.jar' \
    -print -quit)
fi
if [[ -z "${app_jar}" || ! -f "${app_jar}" ]]; then
  printf 'The executable jar was not found. Run ./mvnw verify -P h2local first.\n' >&2
  exit 1
fi

log_file=$(mktemp "${TMPDIR:-/tmp}/vertx-spring-demo.XXXXXX.log")
app_pid=''

cleanup() {
  if [[ -n "${app_pid}" ]] && kill -0 "${app_pid}" 2>/dev/null; then
    kill "${app_pid}" 2>/dev/null || true
    wait "${app_pid}" 2>/dev/null || true
  fi
  rm -f "${log_file}"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

printf 'Starting packaged application %s...\n' "${app_jar}"
java -jar "${app_jar}" >"${log_file}" 2>&1 &
app_pid=$!

for ((attempt = 1; attempt <= 60; attempt++)); do
  if curl --fail --silent "${BASE_URL}/" >/dev/null 2>&1; then
    printf 'Application ready at %s.\n' "${BASE_URL}"
    BASE_URL="${BASE_URL}" ./scripts/demo.sh
    printf '\n[ok] Packaged-jar smoke test passed.\n'
    exit 0
  fi
  if ! kill -0 "${app_pid}" 2>/dev/null; then
    printf 'The application stopped before it became ready.\n\n' >&2
    sed -n '1,240p' "${log_file}" >&2
    exit 1
  fi
  sleep 1
done

printf 'The application did not become ready within 60 seconds.\n\n' >&2
sed -n '1,240p' "${log_file}" >&2
exit 1
