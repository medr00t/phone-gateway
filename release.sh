#!/usr/bin/env bash
# Usage: ./release.sh 1.2.0   -> tags v1.2.0 on the current commit and pushes it; CI builds the release.
set -euo pipefail
v="${1:?version, e.g. 1.2.0}"
[[ "$v" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "version must look like 1.2.0"; exit 1; }
[ -z "$(git status --porcelain)" ] || { echo "commit your changes first"; exit 1; }
git push origin HEAD
git tag -a "v$v" -m "Phone Gateway v$v"
git push origin "v$v"
echo "Release v$v is building: https://github.com/medr00t/phone-gateway/actions"
