#!/usr/bin/env bash
# Promotes an image by writing its tag into the manifests repo.
#
# This is the CI/CD boundary: Jenkins commits to git and stops. ArgoCD watches
# the repo and applies the change. Jenkins never holds cluster credentials.

set -euo pipefail

REPO="" SERVICE="" TAG="" ENVIRONMENT="dev"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo)    REPO="$2"; shift 2 ;;
    --service) SERVICE="$2"; shift 2 ;;
    --tag)     TAG="$2"; shift 2 ;;
    --env)     ENVIRONMENT="$2"; shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

for required in REPO SERVICE TAG; do
  if [[ -z "${!required}" ]]; then
    echo "missing --${required,,}" >&2
    exit 2
  fi
done

if [[ ! "$TAG" =~ ^[0-9a-f]{7,40}$ ]]; then
  echo "refusing to promote '$TAG': tag must be a git SHA, never a moving tag" >&2
  exit 1
fi

WORKTREE="$(mktemp -d)"
trap 'rm -rf "$WORKTREE"' EXIT

# Token comes from the Jenkins credential binding and is never echoed.
git clone --depth 1 "https://${GIT_USER}:${GIT_TOKEN}@${REPO#https://}" "$WORKTREE"

VALUES_FILE="${WORKTREE}/deploy/envs/${ENVIRONMENT}/values/${SERVICE}.yaml"
if [[ ! -f "$VALUES_FILE" ]]; then
  echo "no values file at ${VALUES_FILE}" >&2
  exit 1
fi

CURRENT="$(grep -E '^\s+tag:' "$VALUES_FILE" | head -1 | sed -E 's/.*tag:\s*"?([^"]*)"?.*/\1/')"
if [[ "$CURRENT" == "$TAG" ]]; then
  echo "already at ${TAG}; nothing to promote"
  exit 0
fi

sed -i -E "s|^(\s+tag:\s*).*|\1\"${TAG}\"|" "$VALUES_FILE"

cd "$WORKTREE"
git -c user.email=ci@shopkart.internal -c user.name=jenkins \
    commit -am "chore(${ENVIRONMENT}): ${SERVICE} ${CURRENT} -> ${TAG}"

# Another service may have pushed while we were building; rebase and retry
# rather than failing the whole pipeline on a benign race.
for attempt in 1 2 3; do
  if git push origin HEAD; then
    echo "promoted ${SERVICE} to ${TAG} in ${ENVIRONMENT}"
    exit 0
  fi
  echo "push rejected, rebasing (attempt ${attempt})"
  git pull --rebase origin HEAD
done

echo "could not push after 3 attempts" >&2
exit 1
