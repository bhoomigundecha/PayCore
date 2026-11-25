#!/usr/bin/env bash
# Creates deploy/.env with fresh random secrets. Run once on the server.
# Usage: ./make-env.sh                  (detects the public IP and uses <ip-with-dashes>.sslip.io)
#        ./make-env.sh paycore.example.com   (if you have your own domain)
set -euo pipefail
cd "$(dirname "$0")"

if [ -f .env ]; then
  echo "deploy/.env already exists. Not overwriting it: the card keys must never change once cards exist." >&2
  exit 1
fi

DOMAIN="${1:-}"
if [ -z "$DOMAIN" ]; then
  IP="$(curl -fsS https://api.ipify.org)"
  DOMAIN="${IP//./-}.sslip.io"
fi

key() { openssl rand -base64 32; }

umask 077  # only the current user can read the file
cat > .env <<ENV
PAYCORE_DOMAIN=${DOMAIN}
POSTGRES_PASSWORD=$(openssl rand -hex 24)
PAYCORE_RATE_LIMIT_PER_MINUTE=120
PAYCORE_CARD_ENCRYPTION_KEY=$(key)
PAYCORE_CARD_FINGERPRINT_KEY=$(key)
PAYCORE_CARD_CVC_KEY=$(key)
PAYCORE_STEP_UP_CODE_KEY=$(key)
ENV

echo "Wrote deploy/.env for https://${DOMAIN}"
echo "Back this file up somewhere private: losing the card keys makes existing cards unreadable."
