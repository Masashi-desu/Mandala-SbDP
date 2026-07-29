#!/usr/bin/env bash
set -euo pipefail

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)/lib.sh"

port="${MANDALA_PORT:-4174}"
if [[ "${1:-}" == "--port" && -n "${2:-}" ]]; then
  port="$2"
elif [[ -n "${1:-}" ]]; then
  die "Usage: ./scripts/serve-mandala.sh [--port PORT]"
fi

[[ -f "${MANDALA_REPOSITORY_ROOT}/mandala/generated/sample-app/site/index.html" ]] \
  || die "Sample Mandala is not generated. Run ./scripts/refresh-mandala.sh first."
"${MANDALA_REPOSITORY_ROOT}/scripts/build-site.sh"
[[ -f "${MANDALA_REPOSITORY_ROOT}/site/dist/sample/index.html" ]] \
  || die "Published sample Mandala was not assembled under site/dist/sample."
log "Serving the Pages-ready bundle at http://127.0.0.1:${port}/ and the sample Mandala at http://127.0.0.1:${port}/sample/ (Ctrl+C to stop)."
run_mandala_cli serve --root site/dist --bind 127.0.0.1 --port "${port}"
