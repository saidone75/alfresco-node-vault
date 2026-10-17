#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later

set -euo pipefail

keep_running=false
case "${1:-}" in
  --keep-running) keep_running=true ;;
  "") ;;
  *) echo "Usage: $0 [--keep-running]" >&2; exit 2 ;;
esac
if (( $# > 1 )); then
  echo "Usage: $0 [--keep-running]" >&2
  exit 2
fi

cd -- "$(dirname -- "${BASH_SOURCE[0]}")"

compose=(docker compose --env-file docker/.env -f docker/smoke/compose.yml)

"${compose[@]}" config --quiet

stop_stack() {
  local test_status=$?
  local stop_status=0
  trap - EXIT
  "${compose[@]}" stop || stop_status=$?
  if (( test_status != 0 )); then
    exit "$test_status"
  fi
  exit "$stop_status"
}

if [[ "$keep_running" == false ]]; then
  trap stop_stack EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM
fi

"${compose[@]}" run --build --rm smoke
