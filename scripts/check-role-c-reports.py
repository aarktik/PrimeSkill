"""Require C rating/tag acceptance plus existing B/D/E PostgreSQL coverage."""

import argparse
import json
import runpy
from pathlib import Path

existing = runpy.run_path(str(Path(__file__).with_name("check-role-e-b1-reports.py")))["validate_reports"]
REQUIRED = {
    "surefire-reports": {
        "com.example.toolhub.RatingBrowseIntegrationTest": 9,
        "com.example.toolhub.TagGuardIntegrationTest": 35,
    },
    "failsafe-reports": {
        "com.example.toolhub.RatingBrowsePostgresIT": 9,
        "com.example.toolhub.TagGuardPostgresIT": 35,
        "com.example.toolhub.TagMutationConcurrencyPostgresIT": 22,
        "com.example.toolhub.ToolMetadataContractPostgresIT": 38,
        "com.example.toolhub.ToolMetadataConcurrencyPostgresIT": 11,
    },
}


def validate_reports(target: Path) -> dict:
    result = existing(target)
    for folder, suites in REQUIRED.items():
        for name, minimum in suites.items():
            actual = result[folder]["suites"].get(name)
            if actual is None or actual["tests"] < minimum:
                raise ValueError(f"C integration coverage missing/incomplete: {name}; expected at least {minimum}, got {actual}")
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("target", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = validate_reports(args.target)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps({name: value["totals"] for name, value in result.items()}))
