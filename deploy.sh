#!/usr/bin/env bash
set -Eeuo pipefail

REPO_DIR="/srv/vaultist"
REMOTE_URL="https://github.com/markrai/vaultist"
COMPOSE_DIR="$REPO_DIR/deploy"
SERVICE="vaultist"
HEALTH_URL="http://127.0.0.1:8080/api/v1/status"

cd "$REPO_DIR"

if [[ ! -d .git ]]; then
  echo "Error: $REPO_DIR is not a Git repository." >&2
  exit 1
fi

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Error: the repository has uncommitted changes." >&2
  git status --short
  exit 1
fi

git remote set-url origin "$REMOTE_URL"
git fetch --prune origin

BRANCH="$(git branch --show-current)"
if [[ -z "$BRANCH" ]]; then
  echo "Error: the repository is in detached HEAD state." >&2
  exit 1
fi

git pull --ff-only origin "$BRANCH"

cd "$COMPOSE_DIR"

ENV_FILE=".env"
if [[ -f "$ENV_FILE" ]]; then
  set -a
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%%#*}"
    line="${line#"${line%%[![:space:]]*}"}"
    line="${line%"${line##*[![:space:]]}"}"
    [[ -z "$line" ]] && continue
    if [[ "$line" =~ ^([A-Za-z_][A-Za-z0-9_]*)=(.*)$ ]]; then
      key="${BASH_REMATCH[1]}"
      value="${BASH_REMATCH[2]}"
      if [[ "$value" =~ ^\".*\"$ ]]; then
        value="${value:1:-1}"
      elif [[ "$value" =~ ^\'.*\'$ ]]; then
        value="${value:1:-1}"
      fi
      export "$key=$value"
    fi
  done < "$ENV_FILE"
  set +a
fi

VAULT_HOST_PATH="${VAULT_HOST_PATH:-/srv/Vault}"
if [[ -z "${VAULT_UID:-}" || -z "${VAULT_GID:-}" ]]; then
  if [[ -d "$VAULT_HOST_PATH" ]]; then
    VAULT_UID="$(stat -c '%u' "$VAULT_HOST_PATH")"
    VAULT_GID="$(stat -c '%g' "$VAULT_HOST_PATH")"
    echo "Using vault ownership UID:GID=${VAULT_UID}:${VAULT_GID} for ${VAULT_HOST_PATH}"
  else
    echo "Warning: ${VAULT_HOST_PATH} does not exist; container will run as nonroot (65532)." >&2
    VAULT_UID="65532"
    VAULT_GID="65532"
  fi
fi
export VAULT_UID VAULT_GID

docker compose config --quiet
docker compose up -d --build --remove-orphans

CONTAINER_ID="$(docker compose ps -q "$SERVICE")"
if [[ -z "$CONTAINER_ID" ]]; then
  echo "Error: the $SERVICE container was not created." >&2
  exit 1
fi

for _ in {1..30}; do
  HEALTH="$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$CONTAINER_ID")"

  if [[ "$HEALTH" == "healthy" ]]; then
    curl -fsS "$HEALTH_URL" >/dev/null
    echo
    echo "Vaultist deployed successfully."
    git log -1 --oneline
    docker compose ps
    exit 0
  fi

  if [[ "$HEALTH" == "unhealthy" || "$HEALTH" == "exited" || "$HEALTH" == "dead" ]]; then
    echo "Error: deployment failed with container state: $HEALTH" >&2
    docker compose logs --tail=200 "$SERVICE"
    exit 1
  fi

  sleep 2
done

echo "Error: timed out waiting for Vaultist to become healthy." >&2
docker compose ps
docker compose logs --tail=200 "$SERVICE"
exit 1
