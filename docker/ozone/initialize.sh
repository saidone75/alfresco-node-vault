#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail

# Invoked through the image's entrypoint so OZONE-SITE.XML_* is applied.
# Compose waits for SCM to leave safe mode and for OM to accept RPC requests.
if ! ozone sh volume info "/${OZONE_VOLUME}" >/dev/null 2>&1; then
  ozone sh volume create "/${OZONE_VOLUME}"
fi

for bucket in ${OZONE_BUCKETS}; do
  if ! ozone sh bucket info "/${OZONE_VOLUME}/${bucket}" >/dev/null 2>&1; then
    ozone sh bucket create --layout OBJECT_STORE --type RATIS --replication ONE "/${OZONE_VOLUME}/${bucket}"
  fi
done
