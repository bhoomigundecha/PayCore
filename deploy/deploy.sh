#!/usr/bin/env bash
# Builds and (re)starts the whole stack. Safe to run on every deploy.
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f .env ]; then
  echo "Missing deploy/.env. Copy .env.example to .env and fill it in first." >&2
  exit 1
fi

# TigerBeetle's data file must be formatted exactly once, before the first start.
if ! docker volume inspect paycore_tigerbeetle-data >/dev/null 2>&1; then
  echo "==> First deploy: formatting the TigerBeetle data file"
  docker compose run --rm --no-deps tigerbeetle \
    format --cluster=0 --replica=0 --replica-count=1 /data/0_0.tigerbeetle
fi

echo "==> Building and starting PayCore"
docker compose up -d --build
docker compose ps

set -a; . ./.env; set +a
echo "==> When the app is up: https://${PAYCORE_DOMAIN}/ (Swagger UI) and https://${PAYCORE_DOMAIN}/actuator/health"
