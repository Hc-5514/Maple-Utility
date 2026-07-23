#!/bin/sh
set -eu

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-.env}"

if [ ! -f "$ENV_FILE" ]; then
  echo "$ENV_FILE file is required" >&2
  exit 1
fi

set -a
. "$ENV_FILE"
set +a

: "${SERVER_NAME:?SERVER_NAME is required}"
: "${LETSENCRYPT_EMAIL:?LETSENCRYPT_EMAIL is required}"

RSA_KEY_SIZE="${RSA_KEY_SIZE:-4096}"
COMPOSE="docker compose -f $COMPOSE_FILE --env-file $ENV_FILE"

echo "Creating temporary certificate for $SERVER_NAME"
$COMPOSE run --rm --entrypoint sh certbot -c "
  mkdir -p /etc/letsencrypt/live/$SERVER_NAME && \
  openssl req -x509 -nodes -newkey rsa:$RSA_KEY_SIZE -days 1 \
    -keyout /etc/letsencrypt/live/$SERVER_NAME/privkey.pem \
    -out /etc/letsencrypt/live/$SERVER_NAME/fullchain.pem \
    -subj /CN=$SERVER_NAME
"

echo "Starting application stack"
$COMPOSE up -d postgres redis app nginx

echo "Removing temporary certificate"
$COMPOSE run --rm --entrypoint sh certbot -c "
  rm -rf /etc/letsencrypt/live/$SERVER_NAME \
  /etc/letsencrypt/archive/$SERVER_NAME \
  /etc/letsencrypt/renewal/$SERVER_NAME.conf
"

echo "Requesting Let's Encrypt certificate for $SERVER_NAME"
$COMPOSE run --rm certbot certonly \
  --webroot \
  --webroot-path /var/www/certbot \
  --email "$LETSENCRYPT_EMAIL" \
  --agree-tos \
  --no-eff-email \
  --rsa-key-size "$RSA_KEY_SIZE" \
  --force-renewal \
  -d "$SERVER_NAME"

echo "Reloading nginx"
$COMPOSE exec nginx nginx -s reload

echo "Let's Encrypt certificate initialization complete"
