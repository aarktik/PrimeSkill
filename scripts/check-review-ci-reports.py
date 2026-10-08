"""Require real Surefire/Failsafe results and explicit D/E race coverage."""
import argparse
import json
from pathlib import Path
import xml.etree.ElementTree as ET

REQUIRED = {
    "com.example.toolhub.ReviewPublishingRacePostgresIT": 10,
    "com.example.toolhub.RoleDPostgresIT": 28,
    "com.example.toolhub.service.ReviewPublishingRollbackPostgresIT": 4,
    "com.example.toolhub.service.ReviewServiceLockRollbackPostgresIT": 4,
}
COUNTERS = ("tests", "failures", "errors", "skipped")


def validate_reports(target: Path) -> dict:
    result = {}
    for folder in ("surefire-reports", "failsafe-reports"):
        files = sorted((target / folder).glob("TEST-*.xml"))
        if not files:
            raise ValueError(f"Missing XML reports: {folder}")
        totals = dict.fromkeys(COUNTERS, 0)
        suites = {}
        for file in files:
            suite = ET.parse(file).getroot()
            name = suite.attrib["name"]
            if name in suites:
                raise ValueError(f"Duplicate suite: {name}")
            counts = {key: int(suite.attrib[key]) for key in COUNTERS}
            if any(value < 0 for value in counts.values()):
                raise ValueError(f"Invalid negative count: {name}")
            if any(counts[key] for key in ("failures", "errors", "skipped")):
                raise ValueError(f"Suite failed or skipped: {name}: {counts}")
            suites[name] = counts
            for key in COUNTERS:
                totals[key] += counts[key]
        if totals["tests"] == 0:
            raise ValueError(f"Empty test execution: {folder}")
        result[folder] = {"totals": totals, "suites": suites}
    for name, expected in REQUIRED.items():
        actual = result["failsafe-reports"]["suites"].get(name)
        if actual is None or actual["tests"] != expected:
            raise ValueError(f"Required coverage missing/wrong count: {name}; expected {expected}, got {actual}")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("target", type=Path, help="Maven target directory")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = validate_reports(args.target)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps({name: value["totals"] for name, value in result.items()}))


if __name__ == "__main__":
    main()
