#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later

set -euo pipefail

keep_running=false
backend=s3
backend_selected=false
for argument in "$@"; do
  case "$argument" in
    --keep-running) keep_running=true ;;
    --gridfs|--s3|--ozone|--all)
      if [[ "$backend_selected" == true ]]; then
        echo "Choose only one backend flag: --gridfs, --s3, --ozone or --all" >&2
        exit 2
      fi
      backend=${argument#--}
      backend_selected=true
      ;;
    *) echo "Usage: $0 [--gridfs|--s3|--ozone|--all] [--keep-running]" >&2; exit 2 ;;
  esac
done

cd -- "$(dirname -- "${BASH_SOURCE[0]}")"

compose=(docker compose --env-file docker/.env -f docker/smoke/compose.yml)
if [[ "$backend" != all ]]; then
  compose+=(-f "docker/smoke/compose-${backend}.yml")
fi

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
