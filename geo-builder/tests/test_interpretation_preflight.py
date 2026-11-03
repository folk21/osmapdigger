"""S02 preflight guarantees do not require PBF or geospatial data."""

from pathlib import Path

import pytest

from osmapdigger_geo import pipeline


ROOT = Path(__file__).resolve().parents[2]


def test_invalid_interpretation_fails_before_missing_pbf_or_reader(tmp_path, monkeypatch):
    config = (ROOT / "geo-builder/config/datasets.toml").read_text()
    config = config.replace('interpretation_profile = "full"',
                            'interpretation_profile = "does-not-exist"', 1)
    path = tmp_path / "datasets.toml"
    path.write_text(config.replace('"../../geo-format/schema.sql"',
                                   f'"{(ROOT / "geo-format/schema.sql").as_posix()}"')
                    .replace('"../../geo-format/VERSION"',
                             f'"{(ROOT / "geo-format/VERSION").as_posix()}"')
                    .replace('"metric-profiles.toml"',
                                   f'"{(ROOT / "geo-builder/config/metric-profiles.toml").as_posix()}"')
                    .replace('"preference-profiles.toml"',
                             f'"{(ROOT / "geo-builder/config/preference-profiles.toml").as_posix()}"')
                    .replace('"interpretation-profiles.toml"',
                             f'"{(ROOT / "geo-builder/config/interpretation-profiles.toml").as_posix()}"'),
                    encoding="utf-8")
    monkeypatch.setattr(pipeline, "PbfReader", lambda *args, **kwargs: pytest.fail("PBF read"))
    with pytest.raises(KeyError, match="Unknown interpretation profile"):
        pipeline.build_dataset("andorra", path, ROOT / "geo-builder/config/metrics.toml", skip_map=True)
