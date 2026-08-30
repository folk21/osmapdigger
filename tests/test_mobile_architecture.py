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
    "geo": {"domain"},
    "dataset": {"domain"},
    "map": {"domain"},
    "external": {"domain"},
    "search": {"domain", "geo", "dataset"},
    "analysis": {"domain", "search", "dataset"},
    "preferences": {"domain", "analysis"},
    "presentation": {"domain", "analysis", "preferences"},
    "workspace": {"domain", "analysis", "preferences", "dataset"},
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
