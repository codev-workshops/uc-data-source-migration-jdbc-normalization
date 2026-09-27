#!/usr/bin/env bash
# Starts the loan-service app, waits until it is ready, runs the K6 benchmark, then stops the app.
# Every setting can be overridden through env vars; all of them have defaults.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
RESULTS_DIR="${RESULTS_DIR:-${SCRIPT_DIR}/results}"

export BASE_URL="${BASE_URL:-http://localhost:8080}"
export PARAM_NAME="${PARAM_NAME:-serviceImpl}"
export VUS="${VUS:-10}"
export RAMP_UP="${RAMP_UP:-30s}"
export STEADY_DURATION="${STEADY_DURATION:-1m}"
export RAMP_DOWN="${RAMP_DOWN:-10s}"

READY_TIMEOUT="${READY_TIMEOUT:-120}"
READY_PATH="${READY_PATH:-/api/loans}"
SPRING_PROFILE="${SPRING_PROFILE:-e2e-test}"
K6_BIN="${K6_BIN:-k6}"
MVN_BIN="${MVN_BIN:-mvn}"
if [[ -x "${REPO_ROOT}/mvnw" ]]; then
  MVN_BIN="${REPO_ROOT}/mvnw"
fi

APP_LOG="${RESULTS_DIR}/app.log"
APP_PID=""

log() { printf '[launch] %s\n' "$*" >&2; }

cleanup() {
  local exit_code=$?
  if [[ -n "${APP_PID}" ]] && kill -0 "${APP_PID}" 2>/dev/null; then
    log "stopping app (pid ${APP_PID})"
    pkill -TERM -P "${APP_PID}" 2>/dev/null || true
    kill -TERM "${APP_PID}" 2>/dev/null || true
    for _ in $(seq 1 20); do
      kill -0 "${APP_PID}" 2>/dev/null || break
      sleep 0.5
    done
    if kill -0 "${APP_PID}" 2>/dev/null; then
      log "app did not exit, killing"
      pkill -KILL -P "${APP_PID}" 2>/dev/null || true
      kill -KILL "${APP_PID}" 2>/dev/null || true
    fi
  fi
  log "results in ${RESULTS_DIR}"
  exit "${exit_code}"
}
trap cleanup EXIT INT TERM

command -v "${K6_BIN}" >/dev/null || { log "k6 not found (K6_BIN=${K6_BIN})"; exit 127; }
command -v curl >/dev/null || { log "curl not found"; exit 127; }

mkdir -p "${RESULTS_DIR}"
: > "${APP_LOG}"

log "starting app: ${MVN_BIN} spring-boot:run (profile=${SPRING_PROFILE}), log -> ${APP_LOG}"
(
  cd "${REPO_ROOT}"
  exec "${MVN_BIN}" -q spring-boot:run -Dspring-boot.run.profiles="${SPRING_PROFILE}"
) >"${APP_LOG}" 2>&1 &
APP_PID=$!

log "waiting up to ${READY_TIMEOUT}s for ${BASE_URL}${READY_PATH}"
deadline=$((SECONDS + READY_TIMEOUT))
until [[ "$(curl -s -o /dev/null -w '%{http_code}' "${BASE_URL}${READY_PATH}" || true)" == "200" ]]; do
  if ! kill -0 "${APP_PID}" 2>/dev/null; then
    log "app process exited before becoming ready; see ${APP_LOG}"
    exit 1
  fi
  if (( SECONDS >= deadline )); then
    log "app not ready after ${READY_TIMEOUT}s; see ${APP_LOG}"
    exit 1
  fi
  sleep 1
done
log "app is ready"

log "running k6: VUS=${VUS} RAMP_UP=${RAMP_UP} STEADY_DURATION=${STEADY_DURATION} RAMP_DOWN=${RAMP_DOWN} PARAM_NAME=${PARAM_NAME}"
set +e
RESULTS_DIR="${RESULTS_DIR}" "${K6_BIN}" run \
  --out json="${RESULTS_DIR}/k6-raw.json" \
  "${SCRIPT_DIR}/scripts/loan-endpoints.js" 2>&1 | tee "${RESULTS_DIR}/k6.log"
K6_EXIT="${PIPESTATUS[0]}"
set -e

log "k6 exited with ${K6_EXIT}"
exit "${K6_EXIT}"
