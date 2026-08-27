"""Command-line entry point for dataset discovery, build, and validation."""

from __future__ import annotations

import argparse
from pathlib import Path

from .config import list_dataset_ids
from .package import validate_package
from .pipeline import build_dataset


DEFAULT_DATASETS = Path("geo-builder/config/datasets.toml")
DEFAULT_METRICS = Path("geo-builder/config/metrics.toml")


def create_parser() -> argparse.ArgumentParser:
    """Create the CLI surface without running any GIS or filesystem build work."""
    root = argparse.ArgumentParser(prog="osmapdigger-geo")
    sub = root.add_subparsers(dest="command", required=True)

    list_cmd = sub.add_parser("list-datasets")
    list_cmd.add_argument("--datasets", type=Path, default=DEFAULT_DATASETS)

    build = sub.add_parser("build")
    build.add_argument("dataset")
    build.add_argument("--datasets", type=Path, default=DEFAULT_DATASETS)
    build.add_argument("--metrics", type=Path, default=DEFAULT_METRICS)
    build.add_argument("--skip-map", action="store_true")
    build.add_argument("--map-backend", choices=["auto", "direct", "docker"], default="auto")
    build.add_argument(
        "--settlement-diagnostics",
        action="store_true",
        help="Print detailed nearby-settlement canonicalization diagnostics",
    )
    build.add_argument(
        "--settlement-diagnostics-limit",
        type=int,
        default=20,
        help="Maximum unresolved settlement pairs printed with --settlement-diagnostics",
    )

    validate = sub.add_parser("validate")
    validate.add_argument("package_dir", type=Path)
    return root


def main() -> None:
    """Dispatch the selected command to the owning build/validation API."""
    args = create_parser().parse_args()

    if args.command == "list-datasets":
        for dataset_id in list_dataset_ids(args.datasets):
            print(dataset_id)
        return

    if args.command == "build":
        build_dataset(
            dataset_id=args.dataset,
            datasets_path=args.datasets,
            metrics_path=args.metrics,
            skip_map=args.skip_map,
            map_backend=args.map_backend,
            settlement_diagnostics=args.settlement_diagnostics,
            settlement_diagnostics_limit=args.settlement_diagnostics_limit,
        )
        return

    if args.command == "validate":
        print(validate_package(args.package_dir))
        return

    raise RuntimeError(f"Unhandled command: {args.command}")


if __name__ == "__main__":
    main()
