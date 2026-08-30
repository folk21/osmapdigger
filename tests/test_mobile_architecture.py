from __future__ import annotations

from collections import defaultdict
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
COMMON_MAIN = REPO_ROOT / "mobile/shared/src/commonMain/kotlin"
PACKAGE_PREFIX = "com.permieware.osmapdigger."

# Logical package boundaries for the current pre-Gradle-modularization runtime.
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
    "search": {"domain", "geo", "dataset"},
    "analysis": {"domain", "search", "dataset"},
    "preferences": {"domain", "analysis", "error", "settings"},
    "presentation": {"domain", "analysis", "preferences", "error"},
    "workspace": {"domain", "analysis", "preferences", "dataset", "error"},
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
    for source in COMMON_MAIN.rglob("*.kt"):
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


def test_legacy_unused_dataset_architecture_is_not_present() -> None:
    dataset_package = COMMON_MAIN / "com/permieware/osmapdigger/dataset"
    stale_files = {
        "DatasetConfig.kt",
        "DatasetManager.kt",
        "DatasetMetadata.kt",
    }
    present = sorted(path.name for path in dataset_package.glob("*.kt") if path.name in stale_files)
    assert present == [], f"Unused legacy dataset architecture is still present: {present}"


def test_common_main_does_not_bypass_dependency_checks_with_qualified_references() -> None:
    violations: list[str] = []
    for source in COMMON_MAIN.rglob("*.kt"):
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
        COMMON_MAIN
        / "com/permieware/osmapdigger/settings/ApplicationSettingsSchema.kt"
    ).read_text(encoding="utf-8")

    assert "CREATE TABLE user_preferences" in shared
    assert "external_search_provider" in shared
    assert "candidate_scope_json" in shared
    assert "ALTER TABLE user_preferences" in shared
    for platform_source in (desktop, android):
        assert "ApplicationSettingsSchema" in platform_source
        assert "CREATE TABLE user_preferences" not in platform_source
        assert "CREATE TABLE external_search_provider" not in platform_source
        assert "ALTER TABLE user_preferences" not in platform_source


def test_dataset_package_layout_and_metadata_parser_are_shared() -> None:
    contract = (
        COMMON_MAIN
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
        COMMON_MAIN / "com/permieware/osmapdigger/workspace/AnalysisWorkspaceController.kt",
        COMMON_MAIN / "com/permieware/osmapdigger/ui/OsmapDiggerApp.kt",
        REPO_ROOT / "mobile/androidApp/src/main/kotlin/com/permieware/osmapdigger/MainActivity.kt",
    ]
    violations: list[str] = []
    for path in paths:
        text = path.read_text(encoding="utf-8")
        for token in (".getOrNull()", ".getOrDefault("):
            if token in text:
                violations.append(f"{path.relative_to(REPO_ROOT)} contains {token}")
    assert violations == [], violations
