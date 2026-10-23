#!/bin/sh
set -eu

make check && make check-all && make test-desktop && make build-android
