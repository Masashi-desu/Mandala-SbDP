#!/usr/bin/env bash
set -euo pipefail

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)/lib.sh"
load_local_env

require_command curl
require_command docker
require_command npm

mkdir -p "${MANDALA_RUNTIME_DIR}/logs" "${MANDALA_REPOSITORY_ROOT}/mandala/traces/runtime"

log "Starting PostgreSQL, OpenTelemetry Collector and Jaeger."
compose up --detach --wait postgres jaeger otel-collector
wait_for_http "http://127.0.0.1:13133/" "OpenTelemetry Collector" 60
wait_for_http "http://127.0.0.1:16686/" "Jaeger" 60

export DATABASE_URL="${DATABASE_URL:-${MANDALA_DB_URL}}"
export DATABASE_USERNAME="${DATABASE_USERNAME:-${MANDALA_DB_USERNAME}}"
export DATABASE_PASSWORD="${DATABASE_PASSWORD:-${MANDALA_DB_PASSWORD}}"
export OTEL_EXPORT_ENABLED="${OTEL_EXPORT_ENABLED:-true}"
export MANDALA_USE_GLOBAL_OTEL="${MANDALA_USE_GLOBAL_OTEL:-true}"
export OTEL_EXPORTER_OTLP_TRACES_ENDPOINT="${OTEL_EXPORTER_OTLP_TRACES_ENDPOINT:-http://localhost:4318/v1/traces}"

backend_pid_file="${MANDALA_RUNTIME_DIR}/backend.pid"
backend_marker="${MANDALA_REPOSITORY_ROOT}/sample-app/backend/build/libs/backend-0.1.0-SNAPSHOT.jar"
if is_pid_running "${backend_pid_file}" "${backend_marker}"; then
  log "Sample backend is already running (PID $(pid_from_file "${backend_pid_file}"))."
else
  rm -f "${backend_pid_file}"
  if curl --silent --output /dev/null --max-time 1 "http://127.0.0.1:${BACKEND_PORT:-18080}/"; then
    die "Backend port ${BACKEND_PORT:-18080} is already occupied by an unmanaged process. Change BACKEND_PORT in .env."
  fi
  agent_path="${MANDALA_TOOL_DIR}/opentelemetry-javaagent.jar"
  [[ -f "${agent_path}" ]] || die "OpenTelemetry Java agent is missing. Run ./scripts/setup.sh."
  backend_jar="${MANDALA_REPOSITORY_ROOT}/sample-app/backend/build/libs/backend-0.1.0-SNAPSHOT.jar"
  log "Building the executable sample backend jar."
  (cd "${MANDALA_REPOSITORY_ROOT}" && ./gradlew --console=plain :sample-app:backend:bootJar)
  [[ -f "${backend_jar}" ]] || die "Backend bootJar was not produced: ${backend_jar}"
  log "Starting sample backend on port ${BACKEND_PORT:-18080}."
  (
    cd "${MANDALA_REPOSITORY_ROOT}"
    export BACKEND_PORT="${BACKEND_PORT:-18080}"
    export OTEL_SERVICE_NAME="mandala-sample-backend"
    export OTEL_TRACES_EXPORTER="otlp"
    export OTEL_METRICS_EXPORTER="none"
    export OTEL_LOGS_EXPORTER="none"
    export OTEL_EXPORTER_OTLP_PROTOCOL="http/protobuf"
    export OTEL_EXPORTER_OTLP_ENDPOINT="http://localhost:4318"
    nohup java "-javaagent:${agent_path}" -jar "${backend_jar}" \
      > "${MANDALA_RUNTIME_DIR}/logs/backend.log" 2>&1 &
    write_pid_identity "${backend_pid_file}" "$!" "${backend_marker}"
  )
fi

frontend_pid_file="${MANDALA_RUNTIME_DIR}/frontend.pid"
frontend_marker="npm run dev"
if is_pid_running "${frontend_pid_file}" "${frontend_marker}"; then
  log "Sample frontend is already running (PID $(pid_from_file "${frontend_pid_file}"))."
else
  rm -f "${frontend_pid_file}"
  if curl --silent --output /dev/null --max-time 1 "http://127.0.0.1:${FRONTEND_PORT:-5173}/"; then
    die "Frontend port ${FRONTEND_PORT:-5173} is already occupied by an unmanaged process. Change FRONTEND_PORT in .env."
  fi
  log "Starting sample frontend on port ${FRONTEND_PORT:-5173}."
  (
    cd "${MANDALA_REPOSITORY_ROOT}"
    nohup npm run dev --workspace @mandala/sample-frontend \
      > "${MANDALA_RUNTIME_DIR}/logs/frontend.log" 2>&1 &
    write_pid_identity "${frontend_pid_file}" "$!" "${frontend_marker}"
  )
fi

wait_for_http "http://127.0.0.1:${BACKEND_PORT:-18080}/actuator/health" "Sample backend" 120
wait_for_http "http://127.0.0.1:${BACKEND_PORT:-18080}/v3/api-docs" "Sample backend API" 30
wait_for_http "http://127.0.0.1:${FRONTEND_PORT:-5173}/" "Sample frontend" 60
is_pid_running "${backend_pid_file}" "${backend_marker}" || die "Backend process exited or its identity changed. See .runtime/logs/backend.log."
is_pid_running "${frontend_pid_file}" "${frontend_marker}" || die "Frontend process exited or its identity changed. See .runtime/logs/frontend.log."

log "Environment is ready."
log "Frontend: http://127.0.0.1:${FRONTEND_PORT:-5173}"
log "Backend:  http://127.0.0.1:${BACKEND_PORT:-18080}"
log "Jaeger:   http://127.0.0.1:16686"
