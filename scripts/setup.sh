#!/usr/bin/env bash
set -euo pipefail

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)/lib.sh"

COMPOSE_VERSION="2.39.1"
OTEL_JAVA_AGENT_VERSION="2.16.0"
OTEL_JAVA_AGENT_SHA256="1b0246d3e60b608b07836a9656e1a97bb7d084b088111ef34ecd47483acebcf5"

require_command java
require_command node
require_command npm
require_command docker
require_command curl
require_command git
require_command jq

java_major="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9][0-9]*\).*/\1/p')"
[[ "${java_major}" == "21" ]] || die "Java 21 is required (detected: ${java_major:-unknown})."

node_major="$(node -p 'Number(process.versions.node.split(".")[0])')"
(( node_major >= 24 )) || die "Node.js 24 or newer is required (detected: $(node --version))."

docker info >/dev/null 2>&1 || die "Docker daemon is not running."
[[ -x "${MANDALA_REPOSITORY_ROOT}/gradlew" ]] || die "Gradle Wrapper is missing or not executable."

mkdir -p \
  "${MANDALA_TOOL_DIR}" \
  "${MANDALA_RUNTIME_DIR}/logs" \
  "${MANDALA_REPOSITORY_ROOT}/mandala/cache" \
  "${MANDALA_REPOSITORY_ROOT}/mandala/snapshots/db" \
  "${MANDALA_REPOSITORY_ROOT}/mandala/snapshots/runtime" \
  "${MANDALA_REPOSITORY_ROOT}/mandala/snapshots/ui" \
  "${MANDALA_REPOSITORY_ROOT}/mandala/traces/runtime" \
  "${MANDALA_REPOSITORY_ROOT}/mandala/generated/sample-app"

if [[ ! -f "${MANDALA_REPOSITORY_ROOT}/.env" ]]; then
  cp "${MANDALA_REPOSITORY_ROOT}/.env.example" "${MANDALA_REPOSITORY_ROOT}/.env"
  log "Created .env from .env.example (local development values only)."
else
  log "Keeping existing .env."
fi
load_local_env

if ! docker compose version >/dev/null 2>&1; then
  case "$(uname -s)" in
    Darwin) compose_os="darwin" ;;
    Linux) compose_os="linux" ;;
    *) die "Install Docker Compose v${COMPOSE_VERSION} for this operating system." ;;
  esac
  case "$(uname -m)" in
    arm64|aarch64) compose_arch="aarch64" ;;
    x86_64|amd64) compose_arch="x86_64" ;;
    *) die "Unsupported architecture for Docker Compose: $(uname -m)" ;;
  esac
  compose_asset="docker-compose-${compose_os}-${compose_arch}"
  if [[ ! -x "${MANDALA_TOOL_DIR}/docker-compose" ]]; then
    log "Downloading pinned Docker Compose v${COMPOSE_VERSION}."
    curl --fail --location --retry 3 \
      "https://github.com/docker/compose/releases/download/v${COMPOSE_VERSION}/${compose_asset}" \
      --output "${MANDALA_TOOL_DIR}/docker-compose"
    chmod 0755 "${MANDALA_TOOL_DIR}/docker-compose"
  fi
  checksum_file="${MANDALA_TOOL_DIR}/docker-compose-${COMPOSE_VERSION}-checksums.txt"
  curl --fail --location --retry 3 \
    "https://github.com/docker/compose/releases/download/v${COMPOSE_VERSION}/checksums.txt" \
    --output "${checksum_file}"
  expected_compose_sha="$(awk -v asset="${compose_asset}" '$2 == "*" asset { print $1 }' "${checksum_file}")"
  [[ -n "${expected_compose_sha}" ]] || die "Checksum for ${compose_asset} was not found."
  if command -v sha256sum >/dev/null 2>&1; then
    actual_compose_sha="$(sha256sum "${MANDALA_TOOL_DIR}/docker-compose" | awk '{print $1}')"
  else
    actual_compose_sha="$(shasum -a 256 "${MANDALA_TOOL_DIR}/docker-compose" | awk '{print $1}')"
  fi
  [[ "${actual_compose_sha}" == "${expected_compose_sha}" ]] || die "Docker Compose checksum mismatch."
fi

agent_path="${MANDALA_TOOL_DIR}/opentelemetry-javaagent-${OTEL_JAVA_AGENT_VERSION}.jar"
if [[ ! -f "${agent_path}" ]]; then
  log "Downloading pinned OpenTelemetry Java agent ${OTEL_JAVA_AGENT_VERSION}."
  curl --fail --location --retry 3 \
    "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_JAVA_AGENT_VERSION}/opentelemetry-javaagent.jar" \
    --output "${agent_path}"
fi
if command -v sha256sum >/dev/null 2>&1; then
  actual_agent_sha="$(sha256sum "${agent_path}" | awk '{print $1}')"
else
  actual_agent_sha="$(shasum -a 256 "${agent_path}" | awk '{print $1}')"
fi
[[ "${actual_agent_sha}" == "${OTEL_JAVA_AGENT_SHA256}" ]] || die "OpenTelemetry Java agent checksum mismatch."
ln -sfn "$(basename "${agent_path}")" "${MANDALA_TOOL_DIR}/opentelemetry-javaagent.jar"

log "Resolving Gradle dependencies."
(cd "${MANDALA_REPOSITORY_ROOT}" && ./gradlew --console=plain classes testClasses -x test)

log "Installing pinned npm workspace dependencies."
npm_install_reproducibly

log "Installing Playwright Chromium."
if [[ "$(uname -s)" == "Linux" ]]; then
  (cd "${MANDALA_REPOSITORY_ROOT}" && npm exec --workspace @mandala/playwright-capture playwright install --with-deps chromium)
else
  (cd "${MANDALA_REPOSITORY_ROOT}" && npm exec --workspace @mandala/playwright-capture playwright install chromium)
fi

compose config --quiet
log "Pulling pinned PostgreSQL, OpenTelemetry Collector, Jaeger and helper images."
compose pull postgres jaeger
compose build --pull otel-collector
docker pull alpine:3.22.0 >/dev/null

log "Setup complete. Next: ./scripts/start.sh"
