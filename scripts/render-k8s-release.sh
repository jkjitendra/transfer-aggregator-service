#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ $# -ne 2 || "$1" != production || ! "$2" =~ ^(app|migration)$ ]]; then
  echo "usage: TRANSFER_APP_IMAGE=...@sha256:<64 hex> TRANSFER_MIGRATION_IMAGE=...@sha256:<64 hex> $0 production <app|migration>" >&2
  exit 2
fi

image_pattern='^[a-z0-9./:_-]+@sha256:[a-f0-9]{64}$'
if [[ ! "${TRANSFER_APP_IMAGE:-}" =~ $image_pattern || ! "${TRANSFER_MIGRATION_IMAGE:-}" =~ $image_pattern ]]; then
  echo "Both image references must include immutable sha256 digests" >&2
  exit 2
fi

if [[ "$2" == app ]]; then
  directory="deploy/k8s/overlays/$1"
else
  directory="deploy/k8s/migrations/$1"
fi

kubectl kustomize "$directory" |
  sed -e "s|registry.invalid/transfer-aggregator:replace-with-digest|$TRANSFER_APP_IMAGE|g" \
      -e "s|registry.invalid/transfer-aggregator-migrations:replace-with-digest|$TRANSFER_MIGRATION_IMAGE|g"
