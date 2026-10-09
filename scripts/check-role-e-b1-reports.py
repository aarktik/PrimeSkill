"""Require existing D/E coverage plus E-owned B1 PostgreSQL suites; this is not full B/C acceptance."""
import argparse,json,runpy
from pathlib import Path
base=runpy.run_path(str(Path(__file__).with_name('check-review-ci-reports.py')))['validate_reports']
B1_REQUIRED=(
 'com.example.toolhub.ToolApprovalRevisionPostgresIT',
 'com.example.toolhub.ToolApprovalConcurrencyPostgresIT',
 'com.example.toolhub.ReviewRevisionMigrationPostgresIT',
)
def validate_reports(target: Path) -> dict:
 result=base(target)
 for name in B1_REQUIRED:
  actual=result['failsafe-reports']['suites'].get(name)
  if actual is None or actual['tests']<1:
   raise ValueError('B1 coverage missing or empty: '+name)
 return result
if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__)
 parser.add_argument('target',type=Path);parser.add_argument('--output',type=Path,required=True)
 args=parser.parse_args();result=validate_reports(args.target)
 args.output.parent.mkdir(parents=True,exist_ok=True)
 args.output.write_text(json.dumps(result,indent=2),encoding='utf-8')
 print(json.dumps({name:value['totals'] for name,value in result.items()}))
