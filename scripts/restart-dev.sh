#!/usr/bin/env bash
# Restart the local backend/frontend dev servers: stop then start.
# Adapted from the reference project (behavioural-activation)'s scripts/restart-dev.sh.
#
# Usage: bash scripts/restart-dev.sh [backend|frontend] [--debug]   (default: both, no debug)

set -uo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/dev-common.sh
source "$DIR/lib/dev-common.sh"

# tooling_spec_001_dev_startup_scripts.md (TOOLING-001-AC-13) -- pauses on a keypress before
# this script's window closes, if (and only if) it's about to exit non-zero.
trap pause_on_failure EXIT

# DEV_SCRIPT_NESTED tells stop-dev.sh/start-dev.sh's own pause_on_failure trap to stay silent --
# they run as real subprocesses sharing this terminal, so without this a single failure could
# pause up to three times (TOOLING-001-AC-12). Only this, the outermost invocation, pauses.
# Scoped to each nested call's own environment only (VAR=value cmd), NOT `export`ed into this
# script's own shell -- exporting it instead would mean it's still set when THIS script's own
# trap fires at the bottom, incorrectly suppressing its own pause too.
#
# Exit codes captured independently (not a bare fall-through to the last command's own exit
# code) so this script's own exit status -- and therefore its pause_on_failure trap -- correctly
# reflects a failure in *either* step, not just start-dev.sh's (TOOLING-001-AC-11).
status=0
DEV_SCRIPT_NESTED=1 "$DIR/stop-dev.sh" "$@" || status=1
DEV_SCRIPT_NESTED=1 "$DIR/start-dev.sh" "$@" || status=1
exit $status
