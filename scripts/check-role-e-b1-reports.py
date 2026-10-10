"""Require D/E B1 and B metadata PostgreSQL suites; C acceptance remains pending."""
import argparse,json,runpy
from pathlib import Path
base=runpy.run_path(str(Path(__file__).with_name('check-review-ci-reports.py')))['validate_reports']
B1_REQUIRED=(
 'com.example.toolhub.ToolApprovalRevisionPostgresIT',
 'com.example.toolhub.ToolApprovalConcurrencyPostgresIT',
 'com.example.toolhub.ReviewRevisionMigrationPostgresIT',
)
B_METADATA_REQUIRED = {
 'com.example.toolhub.ToolMetadataContractPostgresIT': 38,
 'com.example.toolhub.ToolMetadataConcurrencyPostgresIT': 11,
}
def validate_reports(target: Path) -> dict:
 result=base(target)
 for name in B1_REQUIRED:
  actual=result['failsafe-reports']['suites'].get(name)
  if actual is None or actual['tests']<1:
   raise ValueError('B1 coverage missing or empty: '+name)
 for name,minimum in B_METADATA_REQUIRED.items():
  actual=result['failsafe-reports']['suites'].get(name)
  if actual is None or actual['tests']<minimum:
   raise ValueError(f'B metadata coverage missing/incomplete: {name}; expected at least {minimum}, got {actual}')
 return result
if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__)
 parser.add_argument('target',type=Path);parser.add_argument('--output',type=Path,required=True)
 args=parser.parse_args();result=validate_reports(args.target)
 args.output.parent.mkdir(parents=True,exist_ok=True)
 args.output.write_text(json.dumps(result,indent=2),encoding='utf-8')
 print(json.dumps({name:value['totals'] for name,value in result.items()}))
