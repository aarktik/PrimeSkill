from pathlib import Path
import runpy,tempfile,unittest,xml.etree.ElementTree as ET
validate=runpy.run_path(str(Path(__file__).with_name('check-role-e-b1-reports.py')))['validate_reports']
old=runpy.run_path(str(Path(__file__).with_name('check-review-ci-reports.py')))['REQUIRED']
b1=['com.example.toolhub.ToolApprovalRevisionPostgresIT','com.example.toolhub.ToolApprovalConcurrencyPostgresIT','com.example.toolhub.ReviewRevisionMigrationPostgresIT']
b_required = {
 'com.example.toolhub.ToolMetadataContractPostgresIT': 38,
 'com.example.toolhub.ToolMetadataConcurrencyPostgresIT': 11,
}
class B1GateTests(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)
  self.write('surefire-reports','example.Unit',1)
  for name,count in old.items():self.write('failsafe-reports',name,count)
  for name in b1:self.write('failsafe-reports',name,1)
  for name,count in b_required.items():self.write('failsafe-reports',name,count)
 def write(self,folder,name,count,skipped=0,failures=0,errors=0):
  p=self.root/folder;p.mkdir(exist_ok=True)
  ET.ElementTree(ET.Element('testsuite',name=name,tests=str(count),failures=str(failures),errors=str(errors),skipped=str(skipped))).write(p/('TEST-'+name+'.xml'))
 def test_valid_b1(self):self.assertEqual(98,validate(self.root)['failsafe-reports']['totals']['tests'])
 def test_each_missing_b1_suite_is_rejected(self):
  for name in b1:
   with self.subTest(name=name):
    p=self.root/'failsafe-reports'/('TEST-'+name+'.xml');p.unlink()
    with self.assertRaisesRegex(ValueError,'B1 coverage'):validate(self.root)
    self.write('failsafe-reports',name,1)
 def test_empty_b1_suite_is_rejected(self):
  self.write('failsafe-reports',b1[0],0)
  with self.assertRaisesRegex(ValueError,'B1 coverage'):validate(self.root)
 def test_skipped_b1_suite_is_rejected(self):
  self.write('failsafe-reports',b1[1],1,1)
  with self.assertRaises(ValueError):validate(self.root)
 def test_each_missing_b_suite_is_rejected(self):
  for name,count in b_required.items():
   with self.subTest(name=name):
    (self.root/'failsafe-reports'/('TEST-'+name+'.xml')).unlink()
    with self.assertRaisesRegex(ValueError,'B metadata coverage'):validate(self.root)
    self.write('failsafe-reports',name,count)
 def test_each_reduced_b_suite_is_rejected(self):
  for name,count in b_required.items():
   for actual in (0,1,count-1):
    with self.subTest(name=name,actual=actual):
     self.write('failsafe-reports',name,actual)
     with self.assertRaisesRegex(ValueError,'B metadata coverage'):validate(self.root)
   self.write('failsafe-reports',name,count)
 def test_additional_b_tests_are_allowed(self):
  for name,count in b_required.items():self.write('failsafe-reports',name,count+1)
  self.assertEqual(100,validate(self.root)['failsafe-reports']['totals']['tests'])
 def test_each_unsuccessful_b_suite_is_rejected(self):
  for name,count in b_required.items():
   for counter in ('skipped','failures','errors'):
    with self.subTest(name=name,counter=counter):
     self.write('failsafe-reports',name,count,**{counter:1})
     with self.assertRaisesRegex(ValueError,'Suite failed or skipped'):validate(self.root)
   self.write('failsafe-reports',name,count)
 def test_b_suite_in_wrong_report_folder_is_rejected(self):
  for name,count in b_required.items():
   with self.subTest(name=name):
    (self.root/'failsafe-reports'/('TEST-'+name+'.xml')).unlink()
    self.write('surefire-reports',name,count)
    with self.assertRaisesRegex(ValueError,'B metadata coverage'):validate(self.root)
    (self.root/'surefire-reports'/('TEST-'+name+'.xml')).unlink()
    self.write('failsafe-reports',name,count)
if __name__=='__main__':unittest.main()
