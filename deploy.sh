#!/bin/bash
cd "$(dirname "$0")" || exit 1

REPO="SeiriHQApp"
OWNER="Sekiguchi-Takashi"
MSG="${1:-update}"

TOKEN="$(git config --global github.token)"
if [ -z "$TOKEN" ]; then
  printf 'github.token is not set\n'
  exit 1
fi

curl -s -o /dev/null -w 'create repo: %{http_code}\n' \
  -X POST \
  -H "Authorization: token $TOKEN" \
  -H "Accept: application/vnd.github+json" \
  -d "{\"name\":\"$REPO\",\"private\":true}" \
  https://api.github.com/user/repos

if [ ! -d .git ]; then
  git init -b main
fi

git config user.name >/dev/null 2>&1 || git config user.name "$OWNER"
git config user.email >/dev/null 2>&1 || git config user.email "$OWNER@users.noreply.github.com"

git remote remove origin 2>/dev/null
git remote add origin "https://$TOKEN@github.com/$OWNER/$REPO.git"

git add -A
git commit -m "$MSG" || true
git branch -M main
git push -u origin main
