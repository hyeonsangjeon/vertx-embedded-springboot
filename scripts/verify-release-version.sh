#!/usr/bin/env bash

set -euo pipefail

tag="${1:-${GITHUB_REF_NAME:-}}"
if [[ -z "${tag}" ]]; then
  printf 'Usage: %s vX.Y.Z\n' "$0" >&2
  exit 1
fi

project_version=$(./mvnw -q -DforceStdout help:evaluate -Dexpression=project.version)
project_version="${project_version//$'\r'/}"

if [[ "${project_version}" == *-SNAPSHOT ]]; then
  printf 'Release tags require a final Maven version; found %s.\n' "${project_version}" >&2
  exit 1
fi

if [[ "${tag}" != "v${project_version}" ]]; then
  printf 'Release tag %s does not match Maven version %s. Expected v%s.\n' \
    "${tag}" "${project_version}" "${project_version}" >&2
  exit 1
fi

printf '[ok] Release tag %s matches Maven version %s.\n' "${tag}" "${project_version}"
