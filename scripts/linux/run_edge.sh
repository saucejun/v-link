#!/usr/bin/env bash
set -euo pipefail

# Optional config file can be passed as first arg, e.g.:
# ./scripts/linux/run_edge.sh ./edge-a.env
# shellcheck disable=SC1090
[[ $# -ge 1 ]] && source "$1"

NODE_ID=${NODE_ID:-edge-a}
BIND=${BIND:-0.0.0.0}
BIND_PORT=${BIND_PORT:-41001}
COORDINATOR_HOST=${COORDINATOR_HOST:-127.0.0.1}
COORDINATOR_PORT=${COORDINATOR_PORT:-40000}
PSK=${PSK:-demo-psk}
TUN_MODE=${TUN_MODE:-linux}
TUN_NAME=${TUN_NAME:-vlink0}
PEERS=${PEERS:-edge-b}
MTU=${MTU:-1400}
RTO_MS=${RTO_MS:-700}
MAX_RETRIES=${MAX_RETRIES:-4}
PROBE_TIMEOUT_MS=${PROBE_TIMEOUT_MS:-4000}
RELAY_PROBE_INTERVAL_MS=${RELAY_PROBE_INTERVAL_MS:-15000}
FORCE_RELAY=${FORCE_RELAY:-false}
JAVA_CMD=${JAVA_CMD:-java}

$JAVA_CMD -version >/dev/null

ARGS="--id ${NODE_ID} --bind ${BIND} --bindPort ${BIND_PORT} --coordinatorHost ${COORDINATOR_HOST} --coordinatorPort ${COORDINATOR_PORT} --psk ${PSK} --tunMode ${TUN_MODE} --tunName ${TUN_NAME} --peers ${PEERS} --mtu ${MTU} --rtoMs ${RTO_MS} --maxRetries ${MAX_RETRIES} --probeTimeoutMs ${PROBE_TIMEOUT_MS} --relayProbeIntervalMs ${RELAY_PROBE_INTERVAL_MS} --forceRelay ${FORCE_RELAY}"

if [[ -x ./gradlew ]]; then
  ./gradlew :edge:run --args="$ARGS"
else
  gradle :edge:run --args="$ARGS"
fi
