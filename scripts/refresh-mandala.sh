#!/usr/bin/env bash
set -euo pipefail

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)/lib.sh"
load_local_env

mode="FULL"
if [[ "${1:-}" == "--incremental" ]]; then
  mode="INCREMENTAL"
elif [[ -n "${1:-}" && "${1}" != "--full" ]]; then
  die "Usage: ./scripts/refresh-mandala.sh [--full|--incremental]"
fi

export MANDALA_DB_USERNAME MANDALA_DB_PASSWORD MANDALA_DB_URL
export MANDALA_CAPTURE_BASE_URL="${MANDALA_CAPTURE_BASE_URL:-http://127.0.0.1:${FRONTEND_PORT:-5173}}"
export MANDALA_CAPTURE_WEB_SERVER_URL="${MANDALA_CAPTURE_WEB_SERVER_URL:-${MANDALA_CAPTURE_BASE_URL}/}"
wait_for_http "http://127.0.0.1:${BACKEND_PORT:-18080}/actuator/health" "Sample backend" 5
wait_for_http "http://127.0.0.1:${FRONTEND_PORT:-5173}/" "Sample frontend" 5

log "Running ${mode} Refresh (source, UI, runtime, PostgreSQL, reconciliation and rendering)."
run_mandala_cli refresh --mode "${mode}"
run_mandala_cli verify
log "Generated sample Mandala: mandala/generated/sample-app/site/index.html"
