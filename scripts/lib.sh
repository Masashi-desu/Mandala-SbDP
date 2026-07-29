#!/usr/bin/env bash

set -euo pipefail

MANDALA_REPOSITORY_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)"
# Shared by scripts that source this file; shellcheck analyzes lib.sh in isolation.
# shellcheck disable=SC2034
MANDALA_RUNTIME_DIR="${MANDALA_REPOSITORY_ROOT}/.runtime"
# shellcheck disable=SC2034
MANDALA_TOOL_DIR="${MANDALA_REPOSITORY_ROOT}/.tools"
# shellcheck disable=SC2034
MANDALA_LOCAL_INFRA_DIR="${MANDALA_REPOSITORY_ROOT}/infra/local"
MANDALA_COMPOSE_FILE="${MANDALA_LOCAL_INFRA_DIR}/compose.yaml"

log() {
  printf '[mandala] %s\n' "$*"
}

die() {
  printf '[mandala] ERROR: %s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "Required command is not available: $1"
}

load_local_env() {
  local env_file="${MANDALA_REPOSITORY_ROOT}/.env"
  [[ -f "${env_file}" ]] || die ".env is missing. Run ./scripts/setup.sh first."
  while IFS='=' read -r key value || [[ -n "${key:-}" ]]; do
    key="${key%$'\r'}"
    value="${value%$'\r'}"
    [[ -z "${key}" || "${key}" == \#* ]] && continue
    [[ "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || die "Invalid key in .env: ${key}"
    if [[ -z "${!key+x}" ]]; then
      value="${value%\"}"
      value="${value#\"}"
      value="${value%\'}"
      value="${value#\'}"
      export "${key}=${value}"
    fi
  done < "${env_file}"
}

compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose --project-directory "${MANDALA_REPOSITORY_ROOT}" \
      --file "${MANDALA_COMPOSE_FILE}" "$@"
  elif [[ -x "${MANDALA_TOOL_DIR}/docker-compose" ]]; then
    "${MANDALA_TOOL_DIR}/docker-compose" --project-directory "${MANDALA_REPOSITORY_ROOT}" \
      --file "${MANDALA_COMPOSE_FILE}" "$@"
  else
    die "Docker Compose is unavailable. Run ./scripts/setup.sh first."
  fi
}

wait_for_http() {
  local url="$1"
  local label="$2"
  local attempts="${3:-90}"
  local status
  for ((status = 1; status <= attempts; status++)); do
    if curl --fail --silent --show-error --max-time 2 "${url}" >/dev/null 2>&1; then
      log "${label} is ready (${url})"
      return 0
    fi
    sleep 1
  done
  die "${label} did not become ready: ${url}"
}

pid_from_file() {
  local pid_file="$1"
  [[ -f "${pid_file}" ]] || return 1
  local raw pid
  raw="$(tr -d '\r' < "${pid_file}")"
  if [[ "${raw}" =~ ^[[:space:]]*([0-9]+)[[:space:]]*$ ]]; then
    printf '%s\n' "${BASH_REMATCH[1]}"
    return 0
  fi
  pid="$(sed -n 's/^pid=//p' "${pid_file}" | head -n 1)"
  [[ "${pid}" =~ ^[0-9]+$ ]] || return 1
  printf '%s\n' "${pid}"
}

process_start_identity() {
  ps -p "$1" -o lstart= 2>/dev/null | sed 's/^[[:space:]]*//;s/[[:space:]]*$//'
}

write_pid_identity() {
  local pid_file="$1" pid="$2" marker="$3" started
  started="$(process_start_identity "${pid}")"
  [[ -n "${started}" ]] || die "Cannot record process start identity for PID ${pid}"
  [[ "${marker}" != *$'\n'* ]] || die "Invalid process identity marker"
  printf 'pid=%s\nstarted=%s\nmarker=%s\n' "${pid}" "${started}" "${marker}" > "${pid_file}"
}

is_pid_running() {
  local pid_file="$1" expected_marker="$2" pid command recorded_start recorded_marker current_start
  pid="$(pid_from_file "${pid_file}")" || return 1
  kill -0 "${pid}" >/dev/null 2>&1 || return 1
  command="$(ps -p "${pid}" -o command= 2>/dev/null)"
  [[ -n "${command}" && "${command}" == *"${expected_marker}"* ]] || return 1
  recorded_start="$(sed -n 's/^started=//p' "${pid_file}" | head -n 1)"
  recorded_marker="$(sed -n 's/^marker=//p' "${pid_file}" | head -n 1)"
  if [[ -n "${recorded_start}" || -n "${recorded_marker}" ]]; then
    [[ "${recorded_marker}" == "${expected_marker}" ]] || return 1
    current_start="$(process_start_identity "${pid}")"
    [[ -n "${current_start}" && "${current_start}" == "${recorded_start}" ]] || return 1
  fi
}

stop_managed_pid() {
  local name="$1" pid_file="$2" expected_marker="$3" pid
  [[ -f "${pid_file}" ]] || return 0
  pid="$(pid_from_file "${pid_file}")" || pid=""
  if [[ -n "${pid}" ]] && is_pid_running "${pid_file}" "${expected_marker}"; then
    log "Stopping ${name} (PID ${pid})."
    if command -v pkill >/dev/null 2>&1; then pkill -TERM -P "${pid}" >/dev/null 2>&1 || true; fi
    kill "${pid}" >/dev/null 2>&1 || true
    for _ in {1..20}; do
      kill -0 "${pid}" >/dev/null 2>&1 || break
      sleep 0.5
    done
    if kill -0 "${pid}" >/dev/null 2>&1; then
      if command -v pkill >/dev/null 2>&1; then pkill -KILL -P "${pid}" >/dev/null 2>&1 || true; fi
      kill -KILL "${pid}" >/dev/null 2>&1 || true
    fi
  elif [[ -n "${pid}" ]] && kill -0 "${pid}" >/dev/null 2>&1; then
    log "Refusing to stop ${name}: PID ${pid} no longer matches the recorded Mandala process identity."
  fi
  rm -f "${pid_file}"
}

run_mandala_cli() {
  (cd "${MANDALA_REPOSITORY_ROOT}" && ./gradlew --console=plain :mandala-cli:run --args="$*")
}

npm_install_reproducibly() {
  if [[ -f "${MANDALA_REPOSITORY_ROOT}/package-lock.json" ]]; then
    (cd "${MANDALA_REPOSITORY_ROOT}" && npm ci)
  else
    (cd "${MANDALA_REPOSITORY_ROOT}" && npm install)
  fi
}
