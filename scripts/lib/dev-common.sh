#!/usr/bin/env bash
# Shared helpers for scripts/start-dev.sh, stop-dev.sh, restart-dev.sh.
# Adapted from the reference project (behavioural-activation)'s scripts/lib/dev-common.sh.
#
# MSYS_NO_PATHCONV=1 disables git bash's MSYS layer from mangling
# slash-prefixed flags (/PID, /FI, /FO, ...) meant for native Windows
# binaries (tasklist.exe, taskkill.exe) into filesystem paths. Every
# invocation of those binaries in this file/its callers must use plain
# single-slash flags -- do NOT "double-slash" (//PID) on top of this, that
# combination is broken: with the var set, MSYS passes // through literally,
# which the Windows binaries themselves don't understand either.
export MSYS_NO_PATHCONV=1

DEV_COMMON_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$DEV_COMMON_DIR/../.." && pwd)"
LOGS_DIR="$REPO_ROOT/logs"

BACKEND_PORT=8090
FRONTEND_PORT=5180
# /api/auth/me is behind Spring Security and correctly returns 401 without a session --
# "ready" here means "got any HTTP response at all" (see wait_for_health below), not "got a 200".
BACKEND_HEALTH_URL="http://localhost:${BACKEND_PORT}/api/auth/me"
FRONTEND_HEALTH_URL="http://localhost:${FRONTEND_PORT}/"
BACKEND_TIMEOUT=90
FRONTEND_TIMEOUT=20
BACKEND_IMAGE="java.exe"
FRONTEND_IMAGE="node.exe"
BACKEND_DEBUG_PORT=5005

ensure_logs_dir() {
  mkdir -p "$LOGS_DIR"
}

# tooling_spec_001_dev_startup_scripts.md (TOOLING-001-AC-13) -- registered via
# `trap pause_on_failure EXIT` in each of start-dev.sh/stop-dev.sh/restart-dev.sh. Blocks on a
# keypress only when the script is about to exit non-zero, so a disposable spawned window
# (double-click, or launched from PowerShell -- both via Windows' .sh file association) stays
# open long enough to read the diagnostic, instead of closing itself the instant the process
# exits. Two guards:
#   - DEV_SCRIPT_NESTED: set by restart-dev.sh before invoking stop-dev.sh/start-dev.sh as real
#     subprocesses (TOOLING-001-AC-12) -- without this, a single failure could pause up to three
#     times (each nested script's own trap, then restart-dev.sh's own trap).
#   - [ -t 0 ]: skips the pause entirely when stdin isn't an interactive terminal
#     (TOOLING-001-AC-13), so a future non-interactive/CI invocation can never hang waiting on a
#     keypress nobody can supply.
pause_on_failure() {
  local status=$?
  if [ "$status" -ne 0 ] && [ -z "${DEV_SCRIPT_NESTED:-}" ] && [ -t 0 ]; then
    printf '\nPress any key to close this window...\n'
    read -n 1 -s -r
  fi
}

# Exports every KEY=VALUE line from the repo-root .env (gitignored) into this shell, if the
# file exists. Comments (#) and blank lines are skipped. Existing environment variables of the
# same name are NOT overridden, so `FOO=bar bash scripts/start-dev.sh` still wins over .env.
load_dotenv() {
  local env_file="$REPO_ROOT/.env"
  [ -f "$env_file" ] || return 0

  local line key value
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in
      '' | '#'*) continue ;;
    esac
    key="${line%%=*}"
    value="${line#*=}"
    if [ -z "${!key+x}" ]; then
      export "$key=$value"
    fi
  done <"$env_file"
}

# Sets TARGET (all/backend/frontend, default all) and DEBUG_MODE (0/1, default 0) from any
# order of a backend|frontend|all positional argument and a --debug flag. Returns non-zero,
# leaving both unset, on any other argument.
parse_args() {
  TARGET="all"
  DEBUG_MODE=0
  local arg
  for arg in "$@"; do
    case "$arg" in
      --debug) DEBUG_MODE=1 ;;
      all | backend | frontend) TARGET="$arg" ;;
      *)
        unset TARGET DEBUG_MODE
        return 1
        ;;
    esac
  done
  return 0
}

# Success iff netstat shows a LISTENING entry for $1.
is_port_listening() {
  local port="$1"
  netstat -ano | grep -E "TCP[[:space:]]+[^[:space:]]*:${port}[[:space:]]" | grep -q "LISTENING"
}

# The PID currently bound to $1, de-duped across IPv4/IPv6 dual-bind lines. Aborts (prints
# to stderr, returns 2) if genuinely ambiguous.
port_owner_pid() {
  local port="$1"
  local pids
  pids=$(netstat -ano \
    | grep -E "TCP[[:space:]]+[^[:space:]]*:${port}[[:space:]].*LISTENING" \
    | awk '{print $NF}' \
    | sort -u)

  local count
  count=$(printf '%s\n' "$pids" | grep -c '[0-9]')

  if [ "$count" -eq 0 ]; then
    return 1
  elif [ "$count" -gt 1 ]; then
    echo "ERROR: ambiguous port owner for :$port -- multiple distinct PIDs found: $pids" >&2
    return 2
  fi

  echo "$pids"
}

# The process image name (e.g. java.exe) for a PID.
tasklist_image_name() {
  local pid="$1"
  tasklist /FI "PID eq $pid" /FO CSV /NH 2>/dev/null \
    | head -n1 \
    | awk -F'","' '{gsub(/^"/, "", $1); print $1}'
}

# Poll $1 every 2s until it returns any HTTP response (not just 2xx -- see BACKEND_HEALTH_URL
# above, this app's API is auth-gated by default) or $2 seconds elapse. $3 is a label for
# progress output.
wait_for_health() {
  local url="$1" timeout_s="$2" label="$3"
  local elapsed=0

  printf "Waiting for %s" "$label"
  while [ "$elapsed" -lt "$timeout_s" ]; do
    local code
    code=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null)
    if [ -n "$code" ] && [ "$code" != "000" ]; then
      echo " ready (${elapsed}s)"
      return 0
    fi
    printf "."
    sleep 2
    elapsed=$((elapsed + 2))
  done

  echo " timed out after ${timeout_s}s"
  return 1
}
