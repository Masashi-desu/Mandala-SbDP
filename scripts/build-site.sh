#!/usr/bin/env bash
set -euo pipefail

source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd -P)/lib.sh"

[[ -d "${MANDALA_REPOSITORY_ROOT}/node_modules" ]] || npm_install_reproducibly
(cd "${MANDALA_REPOSITORY_ROOT}" && npm run site:build)

[[ -f "${MANDALA_REPOSITORY_ROOT}/site/dist/index.html" ]] || die "Official site build did not produce site/dist/index.html."
[[ -f "${MANDALA_REPOSITORY_ROOT}/site/dist/en/index.html" ]] || die "Official site build did not produce site/dist/en/index.html."
[[ -f "${MANDALA_REPOSITORY_ROOT}/site/dist/docs/overview.html" ]] || die "Official site build did not produce site/dist/docs/overview.html."
[[ -f "${MANDALA_REPOSITORY_ROOT}/site/dist/docs/en/overview.html" ]] || die "Official site build did not produce site/dist/docs/en/overview.html."
[[ -f "${MANDALA_REPOSITORY_ROOT}/site/dist/sample/index.html" ]] || die "Published bundle did not produce site/dist/sample/index.html."
if find "${MANDALA_REPOSITORY_ROOT}/site/dist" -type f \
  \( -name 'mandala.json' -o -name 'otlp.json' -o -name 'mandala.yml' -o -name '.env' \) -print -quit | grep -q .; then
  die "The Pages-ready bundle contains a raw graph, trace or local configuration."
fi
log "Pages-ready bundle built: landing pages at / and /en/, documentation under docs/ and docs/en/, and the sample Mandala under sample/."
