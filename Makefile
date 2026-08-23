.PHONY: help check check-all test-python test-mobile test-desktop build-andorra-data build-andorra run-desktop build-android

PYTHON ?= python
GEO_BUILDER_PYTHONPATH = geo-builder/src
DATASET_CONFIG ?= geo-builder/config/datasets.toml
METRICS_CONFIG ?= geo-builder/config/metrics.toml

help:
	@echo "OsmapDigger targets:"
	@echo "  check                Run network-free Python checks"
	@echo "  check-all            Run Python plus configured Kotlin tests"
	@echo "  build-andorra-data   Build SQLite/metadata from local Andorra PBF, skip PMTiles"
	@echo "  build-andorra        Build full Andorra package including PMTiles"
	@echo "  run-desktop          Run Desktop host"
	@echo "  build-android        Build Android debug APK"

check: test-python
	@echo "Lightweight checks passed."

check-all: check test-desktop test-mobile
	@echo "All configured checks passed."

test-python:
	PYTHONPATH=$(GEO_BUILDER_PYTHONPATH) $(PYTHON) -m pytest geo-builder/tests

test-desktop:
	cd mobile && ./gradlew :shared:desktopTest :desktopApp:jvmTest

test-mobile:
	cd mobile && ./gradlew :shared:testAndroidHostTest

build-andorra-data:
	PYTHONPATH=$(GEO_BUILDER_PYTHONPATH) $(PYTHON) -m osmapdigger_geo.cli build andorra --datasets $(DATASET_CONFIG) --metrics $(METRICS_CONFIG) --skip-map

build-andorra:
	PYTHONPATH=$(GEO_BUILDER_PYTHONPATH) $(PYTHON) -m osmapdigger_geo.cli build andorra --datasets $(DATASET_CONFIG) --metrics $(METRICS_CONFIG)

run-desktop:
	cd mobile && ./gradlew :desktopApp:run

build-android:
	cd mobile && ./gradlew :androidApp:assembleDebug
