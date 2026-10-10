"""Regression tests for the CI coverage gate; no database or GitHub access."""
from pathlib import Path
import runpy
import tempfile
import unittest
import xml.etree.ElementTree as ET

module = runpy.run_path(str(Path(__file__).with_name("check-review-ci-reports.py")))
validate = module["validate_reports"]
required = module["REQUIRED"]


class CoverageGateTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.write("surefire-reports", "example.UnitTest", 1)
        for name, count in required.items():
            self.write("failsafe-reports", name, count)

    def write(self, folder, name, tests, **overrides):
        directory = self.root / folder
        directory.mkdir(exist_ok=True)
        counts = dict(tests=tests, failures=0, errors=0, skipped=0)
        counts.update(overrides)
        path = directory / ("TEST-" + name + ".xml")
        ET.ElementTree(ET.Element("testsuite", name=name, **{k: str(v) for k, v in counts.items()})).write(path)
        return path

    def test_valid_coverage(self):
        result = validate(self.root)
        self.assertEqual(46, result["failsafe-reports"]["totals"]["tests"])

    def test_missing_required_suite(self):
        next((self.root / "failsafe-reports").glob("*RacePostgresIT.xml")).unlink()
        with self.assertRaisesRegex(ValueError, "Required coverage"):
            validate(self.root)

    def test_wrong_race_count(self):
        self.write("failsafe-reports", "com.example.toolhub.ReviewPublishingRacePostgresIT", 6)
        with self.assertRaisesRegex(ValueError, "Required coverage"):
            validate(self.root)

    def test_failures_errors_and_skips_are_rejected(self):
        for key in ("failures", "errors", "skipped"):
            with self.subTest(key=key):
                self.write("surefire-reports", "example.UnitTest", 1, **{key: 1})
                with self.assertRaisesRegex(ValueError, "failed or skipped"):
                    validate(self.root)

    def test_missing_surefire(self):
        next((self.root / "surefire-reports").glob("*.xml")).unlink()
        with self.assertRaisesRegex(ValueError, "Missing XML"):
            validate(self.root)

    def test_zero_execution(self):
        self.write("surefire-reports", "example.UnitTest", 0)
        with self.assertRaisesRegex(ValueError, "Empty test execution"):
            validate(self.root)

    def test_duplicate_suite(self):
        path = self.write("surefire-reports", "example.UnitTest", 1)
        path.with_name("TEST-duplicate.xml").write_bytes(path.read_bytes())
        with self.assertRaisesRegex(ValueError, "Duplicate suite"):
            validate(self.root)

    def test_negative_count(self):
        self.write("surefire-reports", "example.UnitTest", -1)
        with self.assertRaisesRegex(ValueError, "negative count"):
            validate(self.root)


if __name__ == "__main__":
    unittest.main()
