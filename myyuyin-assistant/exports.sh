#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
if [ -f "$SCRIPT_DIR/.env" ]; then
    set -a
    . "$SCRIPT_DIR/.env"
    set +a
fi

: "${DB_HOST:?请先在 myyuyin-assistant/.env 中设置 DB_HOST}"
: "${DB_USER:?请先在 myyuyin-assistant/.env 中设置 DB_USER}"
: "${DB_PASSWORD:?请先在 myyuyin-assistant/.env 中设置 DB_PASSWORD}"

export DB_HOST
export DB_PORT="${DB_PORT:-3306}"
export DB_NAME="${DB_NAME:-myyuyinzhushou}"
export DB_USER
export DB_PASSWORD
export DOCKER_PLATFORM="${DOCKER_PLATFORM:-linux/arm64}"
export APP_SERVER_IMAGE="${APP_SERVER_IMAGE:-ghcr.io/your-account/myyuyin-assistant-server:1.0.1}"
