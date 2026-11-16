#!/usr/bin/env bash
# Builds or serves the documentation site with Zensical.
#
#   ./scripts/docs.sh build        Build site in site/
#   ./scripts/docs.sh serve        Live preview of the site
#
# Requires Python 3.11+ and `pip install -r docs/requirements.txt`.
set -euo pipefail

cd "$(dirname "$0")/.."

case "${1:-build}" in
  build)
    rm -rf site
    zensical build --strict
    ;;
  serve)
    zensical serve
    ;;
  *)
    echo "Usage: $0 [build|serve]" >&2
    exit 1
    ;;
esac
