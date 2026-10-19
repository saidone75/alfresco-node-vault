#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later

set -euo pipefail

keep_running=false
backend=default
for argument in "$@"; do
  case "$argument" in
    --keep-running) keep_running=true ;;
    --ozone) backend=ozone ;;
    *) echo "Usage: $0 [--ozone] [--keep-running]" >&2; exit 2 ;;
  esac
done

cd -- "$(dirname -- "${BASH_SOURCE[0]}")"

compose_file=docker/smoke/compose.yml
if [[ "$backend" == ozone ]]; then
  compose_file=docker/smoke/compose-ozone.yml
fi
compose=(docker compose --env-file docker/.env -f "$compose_file")

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
