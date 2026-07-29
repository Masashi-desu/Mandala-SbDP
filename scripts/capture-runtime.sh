#!/usr/bin/env bash
set -euo pipefail

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)/lib.sh"
load_local_env

require_command curl
require_command jq

base_url="http://127.0.0.1:${BACKEND_PORT:-18080}"
trace_file="${MANDALA_REPOSITORY_ROOT}/mandala/traces/runtime/otlp.json"
snapshot_dir="${MANDALA_REPOSITORY_ROOT}/mandala/snapshots/runtime"
capture_dir="$(mktemp -d "${TMPDIR:-/tmp}/mandala-runtime-capture.XXXXXX")"

cleanup() {
  rm -rf "${capture_dir}"
}
trap cleanup EXIT

mkdir -p "$(dirname "${trace_file}")" "${snapshot_dir}"
wait_for_http "${base_url}/actuator/health" "Sample backend" 10

log "Resetting the generated trace file and Collector file handle."
compose stop otel-collector >/dev/null
rm -f "${trace_file}"
docker run --rm --volume mandala-sbdp-traces:/data alpine:3.22.0 \
  rm -f /data/otlp.json >/dev/null
compose up --detach otel-collector >/dev/null
wait_for_http "http://127.0.0.1:13133/" "OpenTelemetry Collector" 60

log "Executing real login, authorization, validation and CRUD requests."
BASE_URL="${base_url}" "${MANDALA_REPOSITORY_ROOT}/sample-app/backend/scripts/smoke-test.sh"

curl --fail --silent --show-error \
  "${base_url}/v3/api-docs" \
  --output "${snapshot_dir}/openapi.json"

curl --fail --silent --show-error \
  --cookie-jar "${capture_dir}/admin.cookie" \
  --header 'Content-Type: application/json' \
  --data '{"username":"local-admin","password":"mandala-admin"}' \
  "${base_url}/api/auth/login" >/dev/null
curl --fail --silent --show-error \
  --cookie "${capture_dir}/admin.cookie" \
  "${base_url}/actuator/mappings" \
  --output "${snapshot_dir}/actuator-mappings.json"

has_complete_project_trace() {
  jq -s -e '
    [.[].resourceSpans[]?.scopeSpans[]?.spans[]?] as $spans
    | ([$spans[]
        | select(any(.attributes[]?;
            .key == "mandala.flow.id" and .value.stringValue == "project.create.success"))
        | .traceId][0]) as $trace
    | ($trace != null)
      and ([$spans[] | select(.traceId == $trace) | .attributes[]?
            | select(.key == "mandala.layer") | .value.stringValue]
           | index("http_server") != null
             and index("application_service") != null
             and index("doma_dao") != null)
      and any($spans[];
        .traceId == $trace
        and any(.attributes[]?;
          .key == "db.system.name" or .key == "db.system" or .key == "db.operation.name"))
  ' "$1" >/dev/null
}

trace_copied=false
candidate_trace="${capture_dir}/otlp.json"
last_fingerprint=""
stable_copies=0
for _ in {1..30}; do
  rm -f "${candidate_trace}"
  if compose cp otel-collector:/var/lib/mandala-traces/otlp.json "${candidate_trace}" >/dev/null 2>&1; then
    if [[ -s "${candidate_trace}" ]] \
      && jq -e . "${candidate_trace}" >/dev/null 2>&1 \
      && has_complete_project_trace "${candidate_trace}"; then
      fingerprint="$(cksum < "${candidate_trace}")"
      if [[ "${fingerprint}" == "${last_fingerprint}" ]]; then
        stable_copies=$((stable_copies + 1))
      else
        last_fingerprint="${fingerprint}"
        stable_copies=1
      fi
      # The Java batch span processor can flush several seconds after the
      # reference trace first appears. Crossing a full quiet window prevents
      # the final request from being imported as a detached partial trace.
      if (( stable_copies >= 8 )); then
        cp "${candidate_trace}" "${trace_file}"
        trace_copied=true
        break
      fi
    else
      last_fingerprint=""
      stable_copies=0
    fi
  fi
  sleep 1
done
[[ "${trace_copied}" == "true" ]] || die "Collector did not write a complete and stable project-create OTLP trace to ${trace_file}. Check .runtime/logs/backend.log and Docker logs."

if grep -Eiq 'mandala-(admin|user)|bearer[[:space:]]+[a-z0-9._-]{8,}|"key"[[:space:]]*:[[:space:]]*"[^"]*(authorization|cookie|password|token)[^"]*"' "${trace_file}"; then
  die "A local credential or sensitive header was found in the captured trace."
fi

# Prove the reference vertical slice is one real distributed trace, rather
# than merely checking that an exporter happened to write some spans.
has_complete_project_trace "${trace_file}" \
  || die "Project-create trace does not contain HTTP, service, DAO and database spans in one trace."

jq -e '.paths | length >= 10' "${snapshot_dir}/openapi.json" >/dev/null
jq -e '.contexts | type == "object"' "${snapshot_dir}/actuator-mappings.json" >/dev/null
log "Runtime traces, OpenAPI and Actuator mappings captured."
