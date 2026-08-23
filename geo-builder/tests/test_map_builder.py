from pathlib import Path

from osmapdigger_geo import map_builder


def test_direct_tilemaker_receives_explicit_profile(tmp_path, monkeypatch):
    source = tmp_path / "source.osm.pbf"
    output = tmp_path / "map.pmtiles"
    source.write_bytes(b"pbf")
    commands = []

    def fake_run(command, check):
        commands.append(command)
        output.write_bytes(b"pmtiles")

    monkeypatch.setattr(map_builder.subprocess, "run", fake_run)

    backend = map_builder.build_pmtiles(source, output, backend="direct")

    assert backend == "direct"
    command = commands[0]
    assert "--config" in command
    assert "--process" in command
    assert str(map_builder.DEFAULT_TILEMAKER_CONFIG.resolve()) in command
    assert str(map_builder.DEFAULT_TILEMAKER_PROCESS.resolve()) in command


def test_default_tilemaker_profile_exists():
    assert map_builder.DEFAULT_TILEMAKER_CONFIG.is_file()
    assert map_builder.DEFAULT_TILEMAKER_PROCESS.is_file()
