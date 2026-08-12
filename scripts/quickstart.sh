#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
BASE_URL="${BASE_URL:-http://localhost:8989}"

cd "${ROOT_DIR}"

if ! command -v curl >/dev/null 2>&1; then
  printf 'curl is required to run this demo.\n' >&2
  exit 1
fi

if curl --fail --silent "${BASE_URL}/" >/dev/null 2>&1; then
  printf 'Using the application already running at %s.\n' "${BASE_URL}"
  BASE_URL="${BASE_URL}" ./scripts/demo.sh
  exit 0
fi

java_major() {
  java -version 2>&1 \
    | awk -F '"' '/version/ { print $2; exit }' \
    | awk -F '[._-]' '{ if ($1 == "1") print $2; else print $1 }'
}

current_java=$(java_major 2>/dev/null || true)
if [[ ! "${current_java}" =~ ^[0-9]+$ ]] || (( current_java < 17 )); then
  if [[ "$(uname -s)" == "Darwin" ]] && java_home=$(/usr/libexec/java_home -v 17 2>/dev/null); then
    export JAVA_HOME="${java_home}"
    export PATH="${JAVA_HOME}/bin:${PATH}"
    current_java=$(java_major)
  fi
fi

if [[ ! "${current_java}" =~ ^[0-9]+$ ]] || (( current_java < 17 )); then
  printf 'Java 17 or newer is required. Current Java major version: %s\n' "${current_java:-unknown}" >&2
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

printf 'Building the executable jar with Java %s...\n' "${current_java}"
./mvnw -q -DskipTests -P h2local clean package

app_jar=$(find target -maxdepth 1 -type f \
  -name 'vertx-embedded-springboot-*.jar' \
  ! -name '*.original' \
  -print -quit)
if [[ -z "${app_jar}" ]]; then
  printf 'The executable jar was not created.\n' >&2
  exit 1
fi

printf 'Starting %s...\n' "${app_jar}"
java -jar "${app_jar}" >"${log_file}" 2>&1 &
app_pid=$!

for ((attempt = 1; attempt <= 60; attempt++)); do
  if curl --fail --silent "${BASE_URL}/" >/dev/null 2>&1; then
    printf 'Application ready at %s.\n' "${BASE_URL}"
    BASE_URL="${BASE_URL}" ./scripts/demo.sh
    exit 0
  fi
  if ! kill -0 "${app_pid}" 2>/dev/null; then
    printf 'The application stopped before it became ready.\n\n' >&2
    cat "${log_file}" >&2
    exit 1
  fi
  sleep 1
done

printf 'The application did not become ready within 60 seconds.\n\n' >&2
cat "${log_file}" >&2
exit 1
