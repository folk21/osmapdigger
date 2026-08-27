#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MOBILE_DIR="$ROOT_DIR/mobile"

DATASET="${1:-}"

case "$DATASET" in
    andorra)
        DATASET_DIR="$ROOT_DIR/data/generated/packages/andorra"
        ;;
    belarus)
        DATASET_DIR="$ROOT_DIR/data/generated/packages/belarus"
        ;;
    *)
        echo "Usage: $0 {andorra|belarus}"
        exit 1
        ;;
esac

if [[ ! -d "$DATASET_DIR" ]]; then
    echo "Dataset directory not found:"
    echo "  $DATASET_DIR"
    exit 1
fi

echo "Stopping Gradle daemons..."
cd "$MOBILE_DIR"
./gradlew --stop || true

echo "Cleaning mobile Gradle build outputs..."
./gradlew clean

echo "Starting Desktop with dataset: $DATASET"
echo "Dataset directory: $DATASET_DIR"

cd "$ROOT_DIR"
OSMAPDIGGER_DATASET_DIR="$DATASET_DIR" make run-desktop
