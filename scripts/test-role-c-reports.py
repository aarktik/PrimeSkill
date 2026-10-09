from pathlib import Path
import runpy
import tempfile
import unittest
import xml.etree.ElementTree as ET

module = runpy.run_path(str(Path(__file__).with_name("check-role-c-reports.py")))
validate = module["validate_reports"]
required = module["REQUIRED"]
de = runpy.run_path(str(Path(__file__).with_name("check-review-ci-reports.py")))["REQUIRED"]
e = runpy.run_path(str(Path(__file__).with_name("check-role-e-b1-reports.py")))["B1_REQUIRED"]


class RoleCGateTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        for name, count in de.items(): self.write("failsafe-reports", name, count)
        for name in e: self.write("failsafe-reports", name, 1)
        for folder, suites in required.items():
            for name, count in suites.items(): self.write(folder, name, count)

    def write(self, folder, name, count, **counters):
        directory = self.root / folder
        directory.mkdir(exist_ok=True)
        attributes = dict(name=name, tests=str(count), failures="0", errors="0", skipped="0")
        attributes.update({key: str(value) for key, value in counters.items()})
        ET.ElementTree(ET.Element("testsuite", attributes)).write(directory / f"TEST-{name}.xml")

    def test_complete_combined_coverage_passes(self):
        result = validate(self.root)
        self.assertEqual(44, result["surefire-reports"]["totals"]["tests"])

    def test_each_missing_suite_is_rejected(self):
        for folder, suites in required.items():
            for name, count in suites.items():
                with self.subTest(name=name):
                    (self.root / folder / f"TEST-{name}.xml").unlink()
                    with self.assertRaisesRegex(ValueError, "C integration coverage"): validate(self.root)
                    self.write(folder, name, count)

    def test_partial_concurrency_execution_is_rejected(self):
        name = "com.example.toolhub.TagMutationConcurrencyPostgresIT"
        self.write("failsafe-reports", name, 21)
        with self.assertRaisesRegex(ValueError, "C integration coverage"): validate(self.root)

    def test_skipped_failed_or_errored_cases_are_rejected(self):
        for counter in ("skipped", "failures", "errors"):
            with self.subTest(counter=counter):
                self.write("failsafe-reports", "com.example.toolhub.TagGuardPostgresIT", 35, **{counter: 1})
                with self.assertRaises(ValueError): validate(self.root)


if __name__ == "__main__": unittest.main()
