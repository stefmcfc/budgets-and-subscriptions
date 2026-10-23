#!/usr/bin/env bash
# Docker-related helpers. Currently just a fail-fast preflight check for
# scripts/start-dev.sh, so a Docker daemon that isn't running or a
# not-yet-healthy Postgres surfaces immediately instead of only after the
# backend's full 90s health-check timeout in dev-common.sh. Extend this file
# with further docker-compose helpers as they're needed, rather than mixing
# docker-specific logic into dev-common.sh.
#
# Requires REPO_ROOT to already be set -- source lib/dev-common.sh first.
# Adapted from the reference project (behavioural-activation)'s scripts/lib/docker-common.sh.

# Success iff the Docker daemon is reachable.
docker_is_running() {
  docker info >/dev/null 2>&1
}

# Container ID for the "postgres" compose service, or empty if it doesn't
# exist (never created, or removed via `docker compose down`).
postgres_container_id() {
  (cd "$REPO_ROOT" && docker compose ps -q postgres 2>/dev/null)
}

# Success iff the postgres container's healthcheck currently reports "healthy"
# (see docker-compose.yml's healthcheck block).
postgres_is_healthy() {
  local cid
  cid=$(postgres_container_id)
  [ -n "$cid" ] || return 1
  local status
  status=$(docker inspect --format '{{.State.Health.Status}}' "$cid" 2>/dev/null)
  [ "$status" = "healthy" ]
}

# Prints a specific diagnostic and returns non-zero unless Docker is running
# and postgres is healthy. Call before starting the backend.
docker_preflight() {
  if ! docker_is_running; then
    echo "Docker isn't running -- start Docker Desktop, then try again." >&2
    return 1
  fi

  local cid
  cid=$(postgres_container_id)
  if [ -z "$cid" ]; then
    echo "Postgres isn't up -- run: docker compose up -d" >&2
    return 1
  fi

  if ! postgres_is_healthy; then
    local status
    status=$(docker inspect --format '{{.State.Health.Status}}' "$cid" 2>/dev/null)
    echo "Postgres container is up but not healthy yet (status: ${status:-unknown})." >&2
    echo "  Wait a few seconds and retry, or check: docker compose logs postgres" >&2
    return 1
  fi

  return 0
}
