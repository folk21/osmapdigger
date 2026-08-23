"""Offline map style generation and external tilemaker process integration.

The module owns map artifact production only; it does not calculate analytical
settlement metrics or alter SQLite search semantics.
"""

from __future__ import annotations

import json
import shutil
import subprocess
from pathlib import Path


STYLE_TEMPLATE = {
    "version": 8,
    "name": "OsmapDigger Offline",
    "sources": {
        "osm": {
            "type": "vector",
            "url": "{{PMTILES_URI}}",
        }
    },
    "layers": [
        {
            "id": "background",
            "type": "background",
            "paint": {"background-color": "#f4f1e8"},
        },
        {
            "id": "landcover",
            "type": "fill",
            "source": "osm",
            "source-layer": "landcover",
            "paint": {"fill-color": "#dce8cf", "fill-opacity": 0.75},
        },
        {
            "id": "landuse",
            "type": "fill",
            "source": "osm",
            "source-layer": "landuse",
            "paint": {"fill-color": "#e6ead7", "fill-opacity": 0.50},
        },
        {
            "id": "park",
            "type": "fill",
            "source": "osm",
            "source-layer": "park",
            "paint": {"fill-color": "#cfe6c3", "fill-opacity": 0.65},
        },
        {
            "id": "water",
            "type": "fill",
            "source": "osm",
            "source-layer": "water",
            "paint": {"fill-color": "#b7d9ef"},
        },
        {
            "id": "waterway",
            "type": "line",
            "source": "osm",
            "source-layer": "waterway",
            "paint": {"line-color": "#8bbbd7", "line-width": 1.2},
        },
        {
            "id": "boundary",
            "type": "line",
            "source": "osm",
            "source-layer": "boundary",
            "paint": {
                "line-color": "#999999",
                "line-width": 0.8,
                "line-dasharray": [2, 2],
            },
        },
        {
            "id": "roads",
            "type": "line",
            "source": "osm",
            "source-layer": "transportation",
            "paint": {"line-color": "#c5b7a5", "line-width": 1.5},
        },
        {
            "id": "buildings",
            "type": "fill",
            "source": "osm",
            "source-layer": "building",
            "minzoom": 13,
            "paint": {"fill-color": "#d5cec4", "fill-opacity": 0.8},
        },
        {
            "id": "places",
            "type": "circle",
            "source": "osm",
            "source-layer": "place",
            "paint": {
                "circle-color": "#546e7a",
                "circle-radius": 2.5,
                "circle-opacity": 0.75,
            },
        },
    ],
}


def write_style_template(path: Path) -> None:
    """Write the glyph-free local MapLibre style with an unresolved PMTiles URI."""
    path.write_text(json.dumps(STYLE_TEMPLATE, indent=2), encoding="utf-8")


TILEMAKER_PROFILE_DIR = Path(__file__).resolve().parents[2] / "tilemaker"
DEFAULT_TILEMAKER_CONFIG = TILEMAKER_PROFILE_DIR / "config.json"
DEFAULT_TILEMAKER_PROCESS = TILEMAKER_PROFILE_DIR / "process.lua"


def build_pmtiles(
    pbf_path: Path,
    output_path: Path,
    backend: str = "auto",
    config_path: Path = DEFAULT_TILEMAKER_CONFIG,
    process_path: Path = DEFAULT_TILEMAKER_PROCESS,
) -> str:
    """Generate PMTiles with OsmapDigger's explicit tilemaker profile.

    tilemaker defaults to looking for ``config.json`` and ``process.lua`` in
    its current working directory. OsmapDigger never relies on that implicit
    behavior: checked-in profile files are always passed with ``--config`` and
    ``--process`` so builds are reproducible from any working directory.

    Args:
        pbf_path: Local OSM PBF used as the map source.
        output_path: PMTiles artifact to create.
        backend: ``auto``, ``direct`` or ``docker``.
        config_path: tilemaker layer/settings JSON.
        process_path: tilemaker Lua tag-processing script.

    Returns:
        The backend actually used (``direct`` or ``docker``).

    Raises:
        FileNotFoundError: If the checked-in tilemaker profile is incomplete.
        RuntimeError: If neither supported backend is available or tilemaker
            completes without producing the requested PMTiles artifact.
    """
    output_path.parent.mkdir(parents=True, exist_ok=True)
    config_path = config_path.resolve()
    process_path = process_path.resolve()

    if not config_path.is_file():
        raise FileNotFoundError(f"tilemaker config not found: {config_path}")
    if not process_path.is_file():
        raise FileNotFoundError(f"tilemaker process script not found: {process_path}")

    selected = backend
    if selected == "auto":
        if shutil.which("tilemaker"):
            selected = "direct"
        elif shutil.which("docker"):
            selected = "docker"
        else:
            raise RuntimeError(
                "Map generation requires tilemaker or Docker. "
                "Use --skip-map for a data-only build."
            )

    if selected == "direct":
        command = [
            "tilemaker",
            "--input",
            str(pbf_path),
            "--output",
            str(output_path),
            "--config",
            str(config_path),
            "--process",
            str(process_path),
        ]
    elif selected == "docker":
        if config_path.parent != process_path.parent:
            raise ValueError("Docker tilemaker config and process files must share one directory")

        source_dir = pbf_path.parent.resolve()
        output_dir = output_path.parent.resolve()
        profile_dir = config_path.parent
        command = [
            "docker",
            "run",
            "--rm",
            "-v",
            f"{source_dir}:/source:ro",
            "-v",
            f"{output_dir}:/output",
            "-v",
            f"{profile_dir}:/profile:ro",
            "ghcr.io/systemed/tilemaker:master",
            "--input",
            f"/source/{pbf_path.name}",
            "--output",
            f"/output/{output_path.name}",
            "--config",
            f"/profile/{config_path.name}",
            "--process",
            f"/profile/{process_path.name}",
        ]
    else:
        raise ValueError(f"Unknown map backend: {backend}")

    print("Running:", " ".join(command), flush=True)
    subprocess.run(command, check=True)

    if not output_path.exists() or output_path.stat().st_size == 0:
        raise RuntimeError("tilemaker did not produce a PMTiles file")

    return selected
