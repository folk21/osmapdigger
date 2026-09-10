from __future__ import annotations

from collections import defaultdict
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
SHARED_COMMON_MAIN = REPO_ROOT / "mobile/shared/src/commonMain/kotlin"
CORE_COMMON_MAIN = REPO_ROOT / "mobile/core/src/commonMain/kotlin"
COMMON_MAIN_ROOTS = (CORE_COMMON_MAIN, SHARED_COMMON_MAIN)
PACKAGE_PREFIX = "com.permieware.osmapdigger."

# Logical package boundaries across the current shared KMP modules.
# This is intentionally a small allow-list: dependency direction is a contract,
# not an incidental consequence of which classes happen to import each other.
ALLOWED_DEPENDENCIES: dict[str, set[str]] = {
    "domain": set(),
    "error": set(),
    "settings": set(),
    "geo": {"domain"},
    "dataset": {"domain", "error"},
    "map": {"domain"},
    "external": {"domain", "error"},
    "notebook": {"domain", "error"},
    "search": {"domain", "geo", "dataset"},
    "analysis": {"domain", "search", "dataset"},
    "preferences": {"domain", "analysis", "error", "settings"},
    "presentation": {"domain", "analysis", "preferences", "error"},
    "workspace": {"domain", "analysis", "preferences", "dataset", "notebook", "error"},
    "runtime": {"dataset", "map", "external"},
    "ui": {
        "domain",
        "analysis",
        "preferences",
        "presentation",
        "search",
        "workspace",
        "runtime",
        "map",
        "external",
        "notebook",
        "error",
    },
}


def _logical_package(qualified_name: str) -> str | None:
    if not qualified_name.startswith(PACKAGE_PREFIX):
        return None
    remainder = qualified_name[len(PACKAGE_PREFIX) :]
    return remainder.split(".", 1)[0]


def _source_dependency_graph() -> dict[str, set[str]]:
    graph: dict[str, set[str]] = defaultdict(set)
    for common_main in COMMON_MAIN_ROOTS:
        for source in common_main.rglob("*.kt"):
            package_name: str | None = None
            imports: list[str] = []
            for line in source.read_text(encoding="utf-8").splitlines():
                if line.startswith("package "):
                    package_name = line.removeprefix("package ").strip()
                elif line.startswith("import "):
                    imports.append(line.removeprefix("import ").strip())

            assert package_name is not None, f"Missing package declaration: {source}"
            owner = _logical_package(package_name)
            assert owner is not None, f"Unexpected package outside OsmapDigger namespace: {source}"
            graph.setdefault(owner, set())

            for imported_name in imports:
                dependency = _logical_package(imported_name)
                if dependency is not None and dependency != owner:
                    graph[owner].add(dependency)
    return dict(graph)


def _find_cycle(graph: dict[str, set[str]]) -> list[str] | None:
    visiting: set[str] = set()
    visited: set[str] = set()
    stack: list[str] = []

    def visit(node: str) -> list[str] | None:
        if node in visiting:
            start = stack.index(node)
            return stack[start:] + [node]
        if node in visited:
            return None

        visiting.add(node)
        stack.append(node)
        for dependency in sorted(graph.get(node, set())):
            cycle = visit(dependency)
            if cycle is not None:
                return cycle
        stack.pop()
        visiting.remove(node)
        visited.add(node)
        return None

    for node in sorted(graph):
        cycle = visit(node)
        if cycle is not None:
            return cycle
    return None


def test_common_main_logical_package_dependencies_are_acyclic_and_explicit() -> None:
    graph = _source_dependency_graph()

    cycle = _find_cycle(graph)
    assert cycle is None, f"Cyclic commonMain dependency: {' -> '.join(cycle or [])}"

    assert set(graph) <= set(ALLOWED_DEPENDENCIES), (
        "Document new logical package owners before adding them: "
        f"{sorted(set(graph) - set(ALLOWED_DEPENDENCIES))}"
    )
    for owner, dependencies in sorted(graph.items()):
        unexpected = dependencies - ALLOWED_DEPENDENCIES[owner]
        assert not unexpected, (
            f"{owner} imports dependencies outside its documented direction: "
            f"{sorted(unexpected)}"
        )



def test_gate6_core_module_owns_domain_and_geo_sources() -> None:
    core_packages = {
        _logical_package(
            next(
                line.removeprefix("package ").strip()
                for line in source.read_text(encoding="utf-8").splitlines()
                if line.startswith("package ")
            )
        )
        for source in CORE_COMMON_MAIN.rglob("*.kt")
    }
    assert core_packages == {"domain", "geo"}

    for package_name in ("domain", "geo"):
        old_path = SHARED_COMMON_MAIN / f"com/permieware/osmapdigger/{package_name}"
        remaining_sources = sorted(old_path.rglob("*.kt")) if old_path.exists() else []
        assert not remaining_sources, (
            f"Gate 6 Kotlin source still lives in :shared/{package_name}: "
            f"{remaining_sources}"
        )


def test_gate6_gradle_module_dependencies_are_acyclic_and_explicit() -> None:
    expected = {
        ":core": set(),
        ":shared": {":core"},
        ":desktopApp": {":core", ":shared"},
        ":androidApp": {":core", ":shared"},
    }
    module_dirs = {module: module.removeprefix(":") for module in expected}
    graph: dict[str, set[str]] = {}
    import re

    for module, directory in module_dirs.items():
        build_text = (REPO_ROOT / "mobile" / directory / "build.gradle.kts").read_text(encoding="utf-8")
        dependencies = {f":{name}" for name in re.findall(r"projects\.([A-Za-z0-9_]+)", build_text)}
        graph[module] = dependencies

    assert graph == expected
    cycle = _find_cycle(graph)
    assert cycle is None, f"Cyclic Gradle module dependency: {' -> '.join(cycle or [])}"


def test_legacy_unused_dataset_architecture_is_not_present() -> None:
    dataset_package = SHARED_COMMON_MAIN / "com/permieware/osmapdigger/dataset"
    stale_files = {
        "DatasetConfig.kt",
        "DatasetManager.kt",
        "DatasetMetadata.kt",
    }
    present = sorted(path.name for path in dataset_package.glob("*.kt") if path.name in stale_files)
    assert present == [], f"Unused legacy dataset architecture is still present: {present}"


def test_common_main_does_not_bypass_dependency_checks_with_qualified_references() -> None:
    violations: list[str] = []
    for common_main in COMMON_MAIN_ROOTS:
        for source in common_main.rglob("*.kt"):
            for line_number, line in enumerate(source.read_text(encoding="utf-8").splitlines(), start=1):
                stripped = line.strip()
                if stripped.startswith("package ") or stripped.startswith("import "):
                    continue
                if PACKAGE_PREFIX in line:
                    violations.append(f"{source.relative_to(REPO_ROOT)}:{line_number}")

    assert violations == [], (
        "Use explicit imports for internal dependencies so the architecture graph can inspect them: "
        f"{violations}"
    )


def test_mobile_readme_lists_all_current_gradle_modules() -> None:
    settings = (REPO_ROOT / "mobile/settings.gradle.kts").read_text(encoding="utf-8")
    readme = (REPO_ROOT / "mobile/README.md").read_text(encoding="utf-8")
    modules = {match.group(1) for match in __import__("re").finditer(r'include\("(:[^"\)]+)"\)', settings)}

    assert modules, "No mobile Gradle modules discovered"
    missing = sorted(module for module in modules if f"`{module}`" not in readme)
    assert missing == [], f"mobile/README.md does not document Gradle modules: {missing}"


def test_settings_schema_semantics_have_one_shared_owner() -> None:
    desktop = (
        REPO_ROOT
        / "mobile/desktopApp/src/jvmMain/kotlin/com/permieware/osmapdigger/desktop/settings/DesktopSettingsDatabase.kt"
    ).read_text(encoding="utf-8")
    android = (
        REPO_ROOT
        / "mobile/androidApp/src/main/kotlin/com/permieware/osmapdigger/settings/AndroidSettingsDatabase.kt"
    ).read_text(encoding="utf-8")
    shared = (
        SHARED_COMMON_MAIN
        / "com/permieware/osmapdigger/settings/ApplicationSettingsSchema.kt"
    ).read_text(encoding="utf-8")

    assert "CREATE TABLE user_preferences" in shared
    assert "external_search_provider" in shared
    assert "candidate_scope_json" in shared
    assert "favorite_settlement" in shared
    assert "favorite_analysis_snapshot" in shared
    assert "note_text" in shared
    assert "ALTER TABLE user_preferences" in shared
    for platform_source in (desktop, android):
        assert "ApplicationSettingsSchema" in platform_source
        assert "CREATE TABLE user_preferences" not in platform_source
        assert "CREATE TABLE external_search_provider" not in platform_source
        assert "ALTER TABLE user_preferences" not in platform_source


def test_dataset_package_layout_and_metadata_parser_are_shared() -> None:
    contract = (
        SHARED_COMMON_MAIN
        / "com/permieware/osmapdigger/dataset/DatasetPackageContract.kt"
    ).read_text(encoding="utf-8")
    assert 'const val METADATA_FILE = "metadata.json"' in contract
    assert 'const val DATABASE_FILE = "georisk.sqlite"' in contract
    assert 'const val STYLE_TEMPLATE_FILE = "style.template.json"' in contract
    assert "DatasetPackageMetadataParser" in contract

    platform_sources = [
        REPO_ROOT
        / "mobile/desktopApp/src/jvmMain/kotlin/com/permieware/osmapdigger/desktop/runtime/DesktopDataset.kt",
        REPO_ROOT
        / "mobile/androidApp/src/main/kotlin/com/permieware/osmapdigger/runtime/AndroidDataset.kt",
    ]
    for source in platform_sources:
        text = source.read_text(encoding="utf-8")
        assert "DatasetPackageLayout" in text
        assert "DatasetPackageMetadataParser" in text
        assert "JSONObject(" not in text
        assert "Json.parseToJsonElement" not in text


def test_meaningful_runtime_paths_do_not_silently_drop_failures() -> None:
    paths = [
        SHARED_COMMON_MAIN / "com/permieware/osmapdigger/workspace/AnalysisWorkspaceController.kt",
        SHARED_COMMON_MAIN / "com/permieware/osmapdigger/ui/OsmapDiggerApp.kt",
        REPO_ROOT / "mobile/androidApp/src/main/kotlin/com/permieware/osmapdigger/MainActivity.kt",
    ]
    violations: list[str] = []
    for path in paths:
        text = path.read_text(encoding="utf-8")
        for token in (".getOrNull()", ".getOrDefault("):
            if token in text:
                violations.append(f"{path.relative_to(REPO_ROOT)} contains {token}")
    assert violations == [], violations


def test_candidate_query_semantics_have_one_shared_owner() -> None:
    shared_path = (
        SHARED_COMMON_MAIN
        / "com/permieware/osmapdigger/dataset/DatasetCandidateQueries.kt"
    )
    shared = shared_path.read_text(encoding="utf-8")
    assert "object DatasetCandidateQueries" in shared
    assert "FROM settlement_metric sm$index" in shared
    assert "LEFT JOIN settlement_metric sm_score" in shared
    assert "SQLITE_SAFE_BIND_PARAMETER_COUNT" in shared

    platform_sources = [
        REPO_ROOT
        / "mobile/desktopApp/src/jvmMain/kotlin/com/permieware/osmapdigger/desktop/runtime/JdbcGeoRepository.kt",
        REPO_ROOT
        / "mobile/androidApp/src/main/kotlin/com/permieware/osmapdigger/runtime/AndroidGeoRepository.kt",
    ]
    for source in platform_sources:
        text = source.read_text(encoding="utf-8")
        assert "DatasetCandidateQueries" in text
        assert "appendCandidatePredicates" not in text
        assert "FROM settlement_metric sm$index" not in text
        assert "LEFT JOIN settlement_metric sm_score" not in text
        assert "SQLITE_SAFE_BIND_PARAMETER_COUNT" not in text


def test_gate5_entry_point_hotspots_stay_below_source_review_threshold() -> None:
    # Gate 5 keeps orchestration entry points small enough that feature responsibilities remain discoverable.
    source_paths = [
        REPO_ROOT
        / "mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/ui/DesktopAnalysisWorkspace.kt",
        REPO_ROOT
        / "mobile/shared/src/commonMain/kotlin/com/permieware/osmapdigger/ui/SearchPane.kt",
        REPO_ROOT
        / "mobile/desktopApp/src/jvmMain/kotlin/com/permieware/osmapdigger/desktop/map/LocalWebMapServer.kt",
    ]
    oversized = {
        str(path.relative_to(REPO_ROOT)): path.stat().st_size
        for path in source_paths
        if path.stat().st_size > 20 * 1024
    }
    assert oversized == {}, f"Gate 5 entry points exceeded the 20 KiB responsibility review threshold: {oversized}"
