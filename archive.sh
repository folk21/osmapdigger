#!/bin/sh
set -eu
ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
OUT=${1:-"$ROOT_DIR/../osmapdigger-FULL.zip"}
cd "$ROOT_DIR/.."
rm -f "$OUT"
zip -qr "$OUT" osmapdigger \
  -x 'osmapdigger/.git/*' \
  -x 'osmapdigger/.idea/*' \
  -x 'osmapdigger/.venv/*' \
  -x 'osmapdigger/**/__pycache__/*' \
  -x 'osmapdigger/**/.pytest_cache/*' \
  -x 'osmapdigger/**/build/*' \
  -x 'osmapdigger/**/*.log' \
  -x 'osmapdigger/mobile/.gradle-dist/*' \
  -x 'osmapdigger/data/source/osm/*' \
  -x 'osmapdigger/data/generated/*' \
  -x 'osmapdigger/osmapdigger_files.txt' \
  -x 'osmapdigger/mobile/.kotlin/*'
echo "$OUT"
