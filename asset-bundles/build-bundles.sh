#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

shopt -s nullglob
bundles=( */ )
shopt -u nullglob

if [ ${#bundles[@]} -eq 0 ]; then
  echo "Keine Bundle-Verzeichnisse gefunden in $(pwd)" >&2
  exit 1
fi

for dir in "${bundles[@]}"; do
  name="${dir%/}"
  zipfile="${name}.zip"

  rm -f "$zipfile"
  zip -r -q "$zipfile" "$name" -x "*.DS_Store"
  echo "Erzeugt: $zipfile"
done
