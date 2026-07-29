#!/usr/bin/env bash
set -euo pipefail

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)/lib.sh"

stop_managed_pid "sample frontend" "${MANDALA_RUNTIME_DIR}/frontend.pid" "npm run dev"
stop_managed_pid "sample backend" "${MANDALA_RUNTIME_DIR}/backend.pid" "${MANDALA_REPOSITORY_ROOT}/sample-app/backend/build/libs/backend-0.1.0-SNAPSHOT.jar"

if docker info >/dev/null 2>&1; then
  log "Stopping local containers (database volume is preserved)."
  compose down --remove-orphans
else
  log "Docker daemon is unavailable; application processes were still stopped."
fi

log "Environment stopped."
