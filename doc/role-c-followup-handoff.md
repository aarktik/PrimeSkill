# Role C — rating browse and tag guards

Date: 9 October 2026 (Asia/Bangkok).

Implementation is delivered on personal branch `supakron_673380061-4_02`.
Implementation/verification source SHA: `75f58444be25d76a77f6a8592ddde1aadb18928f`.
Base: E commit `51d195ea0dbf03bbd942b2eec5f272cf9c71a452`.

- C1 tag guards: `2631d5ba2dc470d07f5c35ede43794bd9cc0ce32`.
- C2 rating browse: `a25486e3acc99aba9e6005d99838e812b2809de1`.
- CI gates and Windows runner: `75f58444be25d76a77f6a8592ddde1aadb18928f`.

The documentation commit follows these three commits. Compare C changes against
E `51d195e`; this branch inherits the existing B/D/E dependency history.
That base already includes D's review/summary contract and shared Tool lock,
B's metadata guards (`ad58522`), E publishing/version/revision code, and the approved navy UI.
Do not apply D's old lock candidate patch again. The earlier C source was `281e3ac`; updating the C branch uses a fast-forward.

## C2: review scores and rating sort

- `/tools` obtains summaries for the current page with one call to
  `ReviewSummaryService.summarizeByToolIds`. An empty page does not call it.
- Cards show the mean to one decimal and the review count. No reviews show
  `ยังไม่มีรีวิว`; the D service retains `avgRating=null`, `reviewCount=0`.
- Rating queries join one grouped review aggregate per Tool and sort
  `avgRating DESC NULLS LAST, tool.id ASC` in the database before pagination.
  Display rounding is never used for ordering.
- Category, escaped keyword, and ANY-tag filters stay available. Tags use EXISTS,
  so multiple matching tags cannot multiply tools or inflate the total count.
- Category is fetched with the page; display summaries use one batch query.
  Tests require at most three statements for page + count + summaries across
  multiple distinct categories, with no category entity fetches.
- PostgreSQL testing exposed nullable keyword parameters binding as binary inside
  CONCAT/LOWER. All C search queries now cast keyword to the HQL string type,
  including newest/popular/relevance queries. Missing/blank keyword works with
  and without tag filters in every sort mode.
- Browse layout allows its grid/search controls to shrink on mobile; ratings are
  readable text using the existing navy theme. The REST ToolResponse shape is unchanged.

## C1: tag mutation and publishing

- Assign and unassign share the existing pessimistic Tool row lock used by B/D/E.
  They refresh the managed Tool, check trusted owner/admin, then require DRAFT.
- Every non-DRAFT mutation returns 409 `INVALID_STATE_TRANSITION`, including
  duplicate assign and missing-association unassign. Other users receive 403
  before the status/association lookup. Existing public tag visibility is retained.
- Association writes then acquire a pessimistic Tag row lock and refresh it.
  DeleteTag acquires the same Tag lock before the in-use check and deletion.
- Lock order: **Tool -> Tag for association writes; Tag only for deleteTag**.
  No Tag-locked path subsequently acquires Tool. Publishing retains its Tool-only lock.
  E must preserve this order in future tag-related writers.
- Assignment committing first makes deletion return in-use 409. Deletion committing
  first makes assignment return 404 `RESOURCE_NOT_FOUND`; it never reports success
  with a lost association. Rollback of either side releases its lock without leaking changes.
- These changes do not advance reviewRevision or change tool metadata/status/viewCount.
  Global Tag rename/update policy is unchanged. There is no tag mutation form in
  this base; the tag fields on Browse are public search filters, not edit controls.
- No schema/index/migration changes are needed; existing review and tool_tags indexes are used.

## Verification and delivery

See `role-c-followup-test-report.md` for the completed run, source hashes,
browser evidence, and exact commands. The committed source manifest is
`role-c-followup-source.json`; it confirms the implementation matches the tested
files, allowing only Git CRLF/LF normalization. New PostgreSQL suites run under the existing
`postgres-it` profile and guarded disposable database configuration.

`scripts/check-role-c-reports.py` requires both C acceptance suites, all 22 C
concurrency scenarios, and B's persisted/concurrency suites in addition to the
existing D/E gates. Build and test now invokes it on HEAD; the separate legacy
Review and publishing workflow's baseline/overlay is not evidence for this work.

Windows PowerShell 5.1 reported null ExitCode after pg_ctl successfully started.
The runner now retains the process handle before waiting and refreshes the process,
so its finally block recognizes and stops its successfully started test cluster.

Local patches are split into C2 ratings, C1 tag guards, and combined verification/docs.
The verification patch requires both feature patches. They target E `51d195e`,
not the old C checkout or today's develop. Review them before applying to a newer base.

Still required from the team: E integrates/reviews this exact source and lock order;
A/D review authorization and review contracts; run GitHub CI on the resulting SHA;
then follow the team's PR/develop process. This delivery does not claim a remote PR,
reviewer approval, shared-database migration, or deployment.
