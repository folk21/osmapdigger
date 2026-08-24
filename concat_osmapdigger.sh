#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_name="$(basename "$repo_dir")"
parent_dir="$(dirname "$repo_dir")"
concat_tool="${OSMAPDIGGER_CONCAT_TOOL:-$HOME/work/python/concat_files_to_txt.py}"
output_path="${1:-$parent_dir/osmapdigger_files.txt}"
python_bin="${PYTHON:-python3}"

if [[ "$repo_name" != "osmapdigger" ]]; then
  echo "Expected repository directory name 'osmapdigger', got '$repo_name'." >&2
  exit 2
fi

if [[ ! -f "$concat_tool" ]]; then
  echo "Concat tool not found: $concat_tool" >&2
  echo "Set OSMAPDIGGER_CONCAT_TOOL to the path of concat_files_to_txt.py." >&2
  exit 2
fi

"$python_bin" "$concat_tool" \
  "$repo_dir" \
  "$output_path" \
  -i .git -i .idea -i .vscode \
  -i __pycache__ -i '*/__pycache__/*' \
  -i .pytest_cache -i '*/.pytest_cache/*' \
  -i .mypy_cache -i .ruff_cache \
  -i .gradle -i '*/.gradle/*' \
  -i .gradle-dist -i '*/.gradle-dist/*' \
  -i .kotlin -i '*/.kotlin/*' \
  -i 'mobile/build/*' -i 'mobile/*/build/*' \
  -i 'geo-builder/build/*' -i 'geo-format/build/*' \
  -i target -i '*/target/*' \
  -i dist -i '*/dist/*' \
  -i '*.egg-info' -i '*/*.egg-info/*' \
  -i .venv -i venv -i env -i 'env*' -i '*/.venv/*' \
  -i .cache -i '*/.cache/*' \
  -i node_modules -i '*/node_modules/*' \
  -i data/source -i '*/data/source/*' \
  -i data/generated -i '*/data/generated/*' \
  -i docs/specs/archive -i '*/docs/specs/archive/*' \
  -i local.properties -i osmapdigger_files.txt \
  -i '*.osm.pbf' -i '*.pmtiles' -i '*.sqlite' -i '*.omd.zip' \
  -e .kt -e .kts -e .py -e .md -e .txt \
  -e .ini -e .toml -e .yaml -e .yml -e .json \
  -e .sh -e .gitignore -e .properties -e .sql -e .xml \
  -e .editorconfig -e Makefile -e VERSION

required_source="osmapdigger_geo/pipeline.py"
if ! grep -Fq "$required_source" "$output_path"; then
  rm -f "$output_path"
  echo "Snapshot validation failed: required source entry is missing: $required_source" >&2
  exit 2
fi

required_spec="docs/specs/active/spec-initial-functional-product.md"
if ! grep -Fq "$required_spec" "$output_path"; then
  rm -f "$output_path"
  echo "Snapshot validation failed: active spec is missing: $required_spec" >&2
  exit 2
fi

echo "Created $output_path"
