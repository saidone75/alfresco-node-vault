#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later

set -euo pipefail

cd -- "$(dirname -- "${BASH_SOURCE[0]}")"

compose=(docker compose --env-file docker/.env -f docker/smoke/compose.yml)

"${compose[@]}" config --quiet
exec "${compose[@]}" run --build --rm smoke
