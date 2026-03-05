#!/usr/bin/env bash
set -euo pipefail

# Optional config file can be passed as first arg, e.g.:
# ./scripts/linux/run_coordinator.sh ./coordinator.env
# shellcheck disable=SC1090
[[ $# -ge 1 ]] && source "$1"

PSK=${PSK:-demo-psk}
BIND=${BIND:-0.0.0.0}
PORT=${PORT:-40000}
PEER_TIMEOUT_SEC=${PEER_TIMEOUT_SEC:-30}
CLEANUP_INTERVAL_SEC=${CLEANUP_INTERVAL_SEC:-5}
JAVA_CMD=${JAVA_CMD:-java}

$JAVA_CMD -version >/dev/null

ARGS="--bind ${BIND} --port ${PORT} --psk ${PSK} --peerTimeoutSec ${PEER_TIMEOUT_SEC} --cleanupIntervalSec ${CLEANUP_INTERVAL_SEC}"

if [[ -x ./gradlew ]]; then
  ./gradlew :coordinator:run --args="$ARGS"
else
  gradle :coordinator:run --args="$ARGS"
fi
