#!/usr/bin/env bash
set -euo pipefail

base_url="${BASE_URL:-http://localhost:8080}"
local_username="${LOCAL_USERNAME:-local-user}"
local_password="${LOCAL_PASSWORD:-mandala-user}"
admin_username="${ADMIN_USERNAME:-local-admin}"
admin_password="${ADMIN_PASSWORD:-mandala-admin}"
check_dir="$(mktemp -d /tmp/mandala-backend-smoke.XXXXXX)"
project_id=""
task_id=""

cleanup() {
  if [[ -n "${task_id}" ]]; then
    curl -sS -b "${check_dir}/user.cookie" -X DELETE "${base_url}/api/tasks/${task_id}" >/dev/null || true
  fi
  if [[ -n "${project_id}" ]]; then
    curl -sS -b "${check_dir}/user.cookie" -X DELETE "${base_url}/api/projects/${project_id}" >/dev/null || true
  fi
  rm -r "${check_dir}"
}
trap cleanup EXIT

assert_status() {
  local expected="$1"
  local actual="$2"
  local operation="$3"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "${operation}: expected HTTP ${expected}, got ${actual}" >&2
    exit 1
  fi
}

health_status="$(curl -sS -o "${check_dir}/health.json" -w '%{http_code}' "${base_url}/actuator/health")"
assert_status 200 "${health_status}" health

unauthenticated_status="$(curl -sS -o "${check_dir}/unauthenticated.json" -w '%{http_code}' "${base_url}/api/projects")"
assert_status 401 "${unauthenticated_status}" unauthenticated-project-list

login_status="$(curl -sS -c "${check_dir}/user.cookie" -o "${check_dir}/login.json" -w '%{http_code}' \
  -H 'X-Mandala-Flow-Id: login.success' \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"${local_username}\",\"password\":\"${local_password}\"}" \
  "${base_url}/api/auth/login")"
assert_status 200 "${login_status}" user-login

validation_status="$(curl -sS -b "${check_dir}/user.cookie" -o "${check_dir}/validation.json" -w '%{http_code}' \
  -H 'X-Mandala-Flow-Id: project.create.validation' \
  -H 'Content-Type: application/json' -d '{"name":"   "}' "${base_url}/api/projects")"
assert_status 400 "${validation_status}" project-validation
[[ "$(jq -r '.code' "${check_dir}/validation.json")" == "validation_failed" ]]

forbidden_status="$(curl -sS -b "${check_dir}/user.cookie" -o "${check_dir}/forbidden.json" -w '%{http_code}' \
  -H 'X-Mandala-Flow-Id: forbidden' \
  "${base_url}/api/projects/1")"
assert_status 403 "${forbidden_status}" project-permission

project_create_status="$(curl -sS -b "${check_dir}/user.cookie" -o "${check_dir}/project.json" -w '%{http_code}' \
  -H 'X-Mandala-Flow-Id: project.create.success' \
  -H 'Content-Type: application/json' \
  -d '{"name":"Backend smoke verification","description":"Created by smoke-test.sh"}' \
  "${base_url}/api/projects")"
assert_status 201 "${project_create_status}" project-create
project_id="$(jq -er '.id' "${check_dir}/project.json")"

project_update_status="$(curl -sS -b "${check_dir}/user.cookie" -o "${check_dir}/project-updated.json" -w '%{http_code}' \
  -H 'X-Mandala-Flow-Id: project.update' \
  -X PUT -H 'Content-Type: application/json' \
  -d '{"name":"Backend smoke verification updated","description":"Doma update"}' \
  "${base_url}/api/projects/${project_id}")"
assert_status 200 "${project_update_status}" project-update

task_create_status="$(curl -sS -b "${check_dir}/user.cookie" -o "${check_dir}/task.json" -w '%{http_code}' \
  -H 'X-Mandala-Flow-Id: task.create' \
  -H 'Content-Type: application/json' \
  -d '{"title":"Smoke-test task","description":"Doma insert"}' \
  "${base_url}/api/projects/${project_id}/tasks")"
assert_status 201 "${task_create_status}" task-create
task_id="$(jq -er '.id' "${check_dir}/task.json")"

task_read_status="$(curl -sS -b "${check_dir}/user.cookie" -o "${check_dir}/task-read.json" -w '%{http_code}' \
  -H 'X-Mandala-Flow-Id: task.detail' \
  "${base_url}/api/tasks/${task_id}")"
assert_status 200 "${task_read_status}" task-read

task_update_status="$(curl -sS -b "${check_dir}/user.cookie" -o "${check_dir}/task-updated.json" -w '%{http_code}' \
  -H 'X-Mandala-Flow-Id: task.update' \
  -X PUT -H 'Content-Type: application/json' \
  -d '{"title":"Smoke-test task updated","description":"Doma update","status":"IN_PROGRESS"}' \
  "${base_url}/api/tasks/${task_id}")"
assert_status 200 "${task_update_status}" task-update

task_status_status="$(curl -sS -b "${check_dir}/user.cookie" -o "${check_dir}/task-done.json" -w '%{http_code}' \
  -H 'X-Mandala-Flow-Id: task.status.change' \
  -X PATCH -H 'Content-Type: application/json' -d '{"status":"DONE"}' \
  "${base_url}/api/tasks/${task_id}/status")"
assert_status 200 "${task_status_status}" task-status
[[ "$(jq -r '.status' "${check_dir}/task-done.json")" == "DONE" ]]

task_delete_status="$(curl -sS -b "${check_dir}/user.cookie" -o /dev/null -w '%{http_code}' \
  -H 'X-Mandala-Flow-Id: task.delete' \
  -X DELETE "${base_url}/api/tasks/${task_id}")"
assert_status 204 "${task_delete_status}" task-delete
task_id=""

project_delete_status="$(curl -sS -b "${check_dir}/user.cookie" -o /dev/null -w '%{http_code}' \
  -H 'X-Mandala-Flow-Id: project.delete' \
  -X DELETE "${base_url}/api/projects/${project_id}")"
assert_status 204 "${project_delete_status}" project-delete

missing_status="$(curl -sS -b "${check_dir}/user.cookie" -o "${check_dir}/missing.json" -w '%{http_code}' \
  -H 'X-Mandala-Flow-Id: not.found' \
  "${base_url}/api/projects/${project_id}")"
assert_status 404 "${missing_status}" deleted-project-read
project_id=""

logout_status="$(curl -sS -b "${check_dir}/user.cookie" -o /dev/null -w '%{http_code}' \
  -X POST "${base_url}/api/auth/logout")"
assert_status 204 "${logout_status}" user-logout

admin_login_status="$(curl -sS -c "${check_dir}/admin.cookie" -o /dev/null -w '%{http_code}' \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"${admin_username}\",\"password\":\"${admin_password}\"}" \
  "${base_url}/api/auth/login")"
assert_status 200 "${admin_login_status}" admin-login

audit_status="$(curl -sS -b "${check_dir}/admin.cookie" -o "${check_dir}/audit.json" -w '%{http_code}' \
  "${base_url}/api/audit-logs?limit=50")"
assert_status 200 "${audit_status}" audit-log-list
[[ "$(jq 'length' "${check_dir}/audit.json")" -gt 0 ]]

openapi_status="$(curl -sS -o "${check_dir}/openapi.json" -w '%{http_code}' "${base_url}/v3/api-docs")"
assert_status 200 "${openapi_status}" openapi
[[ "$(jq '.paths | length' "${check_dir}/openapi.json")" -ge 10 ]]

mappings_status="$(curl -sS -b "${check_dir}/admin.cookie" -o /dev/null -w '%{http_code}' \
  "${base_url}/actuator/mappings")"
assert_status 200 "${mappings_status}" actuator-mappings

echo "backend smoke test passed"
