#!/usr/bin/env bash
set -euo pipefail

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)/lib.sh"

check_only=false
if [[ "${1:-}" == "--check" ]]; then
  check_only=true
elif [[ -n "${1:-}" ]]; then
  die "Usage: ./scripts/update-snapshots.sh [--check]"
fi

if [[ "${check_only}" != "true" ]]; then
  (cd "${MANDALA_REPOSITORY_ROOT}" && ./gradlew --console=plain :mandala-renderer:updateRendererGolden)
fi

"${MANDALA_REPOSITORY_ROOT}/scripts/refresh-mandala.sh" --full

if [[ "${check_only}" == "true" ]]; then
  git -C "${MANDALA_REPOSITORY_ROOT}" diff --exit-code -- \
    mandala/generated/sample-app mandala/snapshots || die "Generated snapshots are stale."
  untracked="$(git -C "${MANDALA_REPOSITORY_ROOT}" ls-files --others --exclude-standard -- \
    mandala/generated/sample-app mandala/snapshots)"
  [[ -z "${untracked}" ]] || die "Generated snapshots are untracked:\n${untracked}"
  log "Generated snapshots match the repository."
else
  log "Snapshots regenerated. Review git diff before committing."
fi
