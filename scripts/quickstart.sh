#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
BASE_URL="${BASE_URL:-http://localhost:8989}"

cd "${ROOT_DIR}"

if ! command -v curl >/dev/null 2>&1; then
  printf 'curl is required to run this demo.\n' >&2
  exit 1
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

if curl --fail --silent "${BASE_URL}/" >/dev/null 2>&1; then
  printf 'An application is already running at %s.\n' "${BASE_URL}" >&2
  printf 'Run ./scripts/demo.sh to use it, or stop it before verifying the packaged jar.\n' >&2
  exit 1
fi

printf 'Building and testing the executable jar with Java %s...\n' "${current_java}"
./mvnw -q -P h2local clean verify

BASE_URL="${BASE_URL}" ./scripts/packaged-jar-smoke.sh
