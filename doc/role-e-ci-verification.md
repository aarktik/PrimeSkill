# Role E CI verification — 7 October 2026

Commit: `fd67569398e1a06d944715e9f1c98ca998b1a2e9`, branch `thaninton_673380043-6_02`.

[Build and test run 37653267910](https://github.com/aarktik/PrimeSkill/actions/runs/37653267910) is **completed / success**, verified via GitHub Actions REST run and jobs endpoints.

## Verified job and step results

- `verify`: success. Maven Verify, Docker image build, container startup/database restart persistence, disposal of this run's containers/volumes and report upload all succeeded.
- `postgres-integration`: success. PostgreSQL service initialization, Maven PostgreSQL publishing regressions and report upload all succeeded.
- `Container diagnostics` was skipped as expected because it runs on failure only. This is not a skipped application test count.

The workflow at this commit configures Temurin Java 17 and a PostgreSQL 17 Alpine service. These are CI environment results, separate from local Java 26.0.1/PostgreSQL 18.6 runs.

## Scope

This verifies the workflow for the E tree at the exact SHA above, including its V7/V8 SQL fixtures. It does not run the standalone D or D+E composed harnesses under `test/` automatically, does not apply a shared migration and does not validate a full D+E production merge. New rollback cases added after this commit require their own run; this result cannot certify later changes.

The REST status/jobs were inspected; artifact XML contents were not downloaded. Therefore this report does not assign a CI test count from the local 269 result. Use the uploaded test-report artifacts for exact CI counts.

Recheck with GitHub's run page or read-only API:

```text
GET /repos/aarktik/PrimeSkill/actions/runs/37653267910
GET /repos/aarktik/PrimeSkill/actions/runs/37653267910/jobs?per_page=20
```
