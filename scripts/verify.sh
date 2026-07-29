#!/usr/bin/env bash
set -euo pipefail

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)/lib.sh"
load_local_env

started_backend=false
started_frontend=false
cleanup_verify() {
  if [[ "${started_backend}" == "true" && "${started_frontend}" == "true" ]]; then
    "${MANDALA_REPOSITORY_ROOT}/scripts/stop.sh" || true
    return
  fi
  if [[ "${started_frontend}" == "true" ]]; then
    stop_managed_pid "sample frontend" "${MANDALA_RUNTIME_DIR}/frontend.pid" "npm run dev" || true
  fi
  if [[ "${started_backend}" == "true" ]]; then
    stop_managed_pid "sample backend" "${MANDALA_RUNTIME_DIR}/backend.pid" "${MANDALA_REPOSITORY_ROOT}/sample-app/backend/build/libs/backend-0.1.0-SNAPSHOT.jar" || true
  fi
}
trap cleanup_verify EXIT

require_command java
require_command node
require_command npm
require_command docker
require_command curl
require_command jq

# Compatibility tests intentionally discover the checked-out sample through an
# environment variable so the reusable analyzers never hard-code this repo.
export MANDALA_REPOSITORY_ROOT

log "Verifying repository responsibility boundaries."
for required_path in \
  platform/README.md \
  platform/java/mandala-model \
  platform/java/mandala-core \
  platform/java/mandala-cli \
  platform/playwright-capture \
  platform/agent-skills \
  mandala/README.md \
  mandala/config/mandala.yml \
  infra/local/compose.yaml \
  site/package.json; do
  [[ -e "${MANDALA_REPOSITORY_ROOT}/${required_path}" ]] \
    || die "Required responsibility boundary is missing: ${required_path}"
done
for legacy_path in mandala-core mandala-cli tools docker docker-compose.yml output; do
  [[ ! -e "${MANDALA_REPOSITORY_ROOT}/${legacy_path}" ]] \
    || die "Legacy root path must be migrated: ${legacy_path}"
done

log "Running Java unit, integration and Golden tests."
(cd "${MANDALA_REPOSITORY_ROOT}" && ./gradlew --console=plain check)

log "Running TypeScript unit tests, type checks and production builds."
npm_install_reproducibly
(cd "${MANDALA_REPOSITORY_ROOT}" && npm test && npm run typecheck && npm run build)

backend_ready=false
frontend_ready=false
if curl --fail --silent --max-time 2 "http://127.0.0.1:${BACKEND_PORT:-18080}/actuator/health" >/dev/null 2>&1; then
  is_pid_running \
    "${MANDALA_RUNTIME_DIR}/backend.pid" \
    "${MANDALA_REPOSITORY_ROOT}/sample-app/backend/build/libs/backend-0.1.0-SNAPSHOT.jar" \
    || die "Backend endpoint is served by an unmanaged process. Change BACKEND_PORT in .env."
  backend_ready=true
fi
if curl --fail --silent --max-time 2 "http://127.0.0.1:${FRONTEND_PORT:-5173}/" >/dev/null 2>&1; then
  is_pid_running "${MANDALA_RUNTIME_DIR}/frontend.pid" "npm run dev" \
    || die "Frontend endpoint is served by an unmanaged process. Change FRONTEND_PORT in .env."
  frontend_ready=true
fi
if [[ "${backend_ready}" != "true" || "${frontend_ready}" != "true" ]]; then
  [[ "${backend_ready}" == "true" ]] || started_backend=true
  [[ "${frontend_ready}" == "true" ]] || started_frontend=true
  "${MANDALA_REPOSITORY_ROOT}/scripts/start.sh"
fi

log "Running real PostgreSQL catalog and sample compatibility integration tests."
MANDALA_TEST_POSTGRES_URL="${MANDALA_DB_URL}" \
MANDALA_TEST_POSTGRES_USER="${MANDALA_DB_USERNAME}" \
MANDALA_TEST_POSTGRES_PASSWORD="${MANDALA_DB_PASSWORD}" \
  ./gradlew --console=plain \
    :mandala-postgres:test --tests io.github.mandala.sbdp.postgres.PostgresIntegrationTest \
    :mandala-spring:test --tests io.github.mandala.sbdp.spring.SampleSpringCompatibilityTest \
    :mandala-doma:test --tests io.github.mandala.sbdp.doma.SampleDomaCompatibilityTest \
    --rerun-tasks

log "Running deterministic Playwright capture and a real full-stack Mandala Refresh."
"${MANDALA_REPOSITORY_ROOT}/scripts/refresh-mandala.sh" --full

log "Rechecking graph integrity, bidirectional links, custom references and secret masking."
run_mandala_cli verify
[[ -s "${MANDALA_REPOSITORY_ROOT}/mandala/generated/sample-app/graph/mandala.json" ]] \
  || die "Documentation Graph was not generated."
[[ -f "${MANDALA_REPOSITORY_ROOT}/mandala/generated/sample-app/site/index.html" ]] \
  || die "Sample Mandala site was not generated."

"${MANDALA_REPOSITORY_ROOT}/scripts/build-site.sh"
log "Verification passed."
