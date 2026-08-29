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
  -x 'osmapdigger/**/osmapdigger_geo_builder.egg-info/*' \
  -x 'osmapdigger/**/.pytest_cache/*' \
  -x 'osmapdigger/**/build/*' \
  -x 'osmapdigger/**/*.log' \
  -x 'osmapdigger/mobile/.gradle-dist/*' \
  -x 'osmapdigger/data/source/osm/*' \
  -x 'osmapdigger/data/generated/*' \
  -x 'osmapdigger/osmapdigger_files.txt' \
  -x 'osmapdigger/mobile/.kotlin/*' \
  -x 'osmapdigger/mobile/.gradle/*' \
  -x 'osmapdigger/mobile/.DS_Store' \
  -x 'osmapdigger/.DS_Store'
echo "$OUT"
