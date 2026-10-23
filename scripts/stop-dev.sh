#!/usr/bin/env bash
# Stop the local backend/frontend dev servers. Always re-resolves the live
# port-owner PID rather than trusting a saved pid file, and refuses to kill
# a PID whose process image doesn't match what's expected. Adapted from the
# reference project (behavioural-activation)'s scripts/stop-dev.sh.
#
# Usage: bash scripts/stop-dev.sh [backend|frontend] [--debug]   (default: both;
# --debug is accepted and ignored, so restart-dev.sh --debug can pass it through)

set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/dev-common.sh
source "$DIR/lib/dev-common.sh"

# tooling_spec_001_dev_startup_scripts.md (TOOLING-001-AC-13) -- pauses on a keypress before
# this script's window closes, if (and only if) it's about to exit non-zero.
trap pause_on_failure EXIT

usage() {
  echo "Usage: $(basename "$0") [backend|frontend] [--debug]" >&2
  exit 1
}

parse_args "$@" || usage
target="$TARGET"

stop_service() {
  local name="$1" port="$2" expected_image="$3"

  if ! is_port_listening "$port"; then
    echo "$name not running (nothing listening on :$port)"
    rm -f "$LOGS_DIR/${name}.pid"
    return 0
  fi

  local pid
  pid=$(port_owner_pid "$port")
  if [ -z "${pid:-}" ]; then
    echo "$name: could not resolve a PID for :$port -- leaving it alone" >&2
    return 1
  fi

  local image
  image=$(tasklist_image_name "$pid")
  if [ "$image" != "$expected_image" ]; then
    echo "$name: refusing to kill pid $pid on :$port -- expected image '$expected_image', found '${image:-unknown}'." >&2
    echo "  Investigate manually:  MSYS_NO_PATHCONV=1 tasklist /FI \"PID eq $pid\"" >&2
    echo "  If safe to kill:       MSYS_NO_PATHCONV=1 taskkill /PID $pid /F" >&2
    return 1
  fi

  taskkill /PID "$pid" /T /F >/dev/null
  rm -f "$LOGS_DIR/${name}.pid"
  echo "$name stopped (was pid $pid on :$port)"
}

status=0
if [ "$target" = "all" ] || [ "$target" = "backend" ]; then
  stop_service "backend" "$BACKEND_PORT" "$BACKEND_IMAGE" || status=1
fi
if [ "$target" = "all" ] || [ "$target" = "frontend" ]; then
  stop_service "frontend" "$FRONTEND_PORT" "$FRONTEND_IMAGE" || status=1
fi

exit $status
