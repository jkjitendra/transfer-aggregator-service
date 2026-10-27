#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
file=deploy/local/.env
if [[ -e "$file" ]]; then
  echo "$file already exists; refusing to replace local keys" >&2
  exit 1
fi
umask 077
{
  printf 'DATABASE_PASSWORD=%s\n' "$(openssl rand -hex 24)"
  printf 'REDIS_PASSWORD=%s\n' "$(openssl rand -hex 24)"
  printf 'KEYCLOAK_ADMIN_PASSWORD=%s\n' "$(openssl rand -hex 24)"
  printf 'TOKEN_SECRET=%s\n' "$(openssl rand -hex 48)"
  printf 'WORKFLOW_ENCRYPTION_KEY=%s\n' "$(openssl rand -base64 32)"
} > "$file"
echo "Generated $file with local-only credentials"
