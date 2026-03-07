# Test Plan: PR Bot Benchmark

## Unit Tests (automated, fast, no I/O)

### Text Processing
- `TextNormalizerTest` — markdown stripping, URL removal, case folding, whitespace collapsing
- `WinnowingHashStrategyTest` — k-gram generation, rolling hash, window selection, Jaccard similarity, fingerprint determinism, short-text edge cases
- `NormalizedTextStrategyTest` — Jaro-Winkler edge cases (identical, empty, completely different, transpositions)
- `ExactMatchStrategyTest` — identical / different / whitespace-only inputs
- `FileLineStrategyTest` — same file+line, same file only, different file, null line numbers

### Service Logic
- `ExemplarRepoServiceTest` — GitHub URL parsing (https://github.com/owner/repo, trailing slashes, .git suffix)
- `ReportingServiceTest` — precision/recall/F1 calculation including divide-by-zero, zero golden entries, perfect recall
- `GradingServiceTest` — stars validation (reject <0 or >5), verdict validation

### GitHub App Auth
- `GitHubAppAuthServiceTest` — JWT generation with a generated RSA test key (verify iss claim, exp timing); token caching (only refresh after expiry)

## Integration Tests (Spring context + database)

### Repository Layer (`@DataJpaTest`)
- `ExemplarRepoRepositoryTest` — findByOwnerAndRepoName uniqueness
- `SuitePrRepositoryTest` — findBySuiteId, unique constraint on (suite_id, original_pr_number)
- `BenchmarkRunRepositoryTest` — findByStatus
- `ReviewCommentRepositoryTest` — findByReplayPrId, findByReplayPrRunId

### API Layer (`@SpringBootTest` + `MockMvc` + Testcontainers Postgres)
- `ExemplarRepoControllerTest` — GET /api/repos, POST /api/repos (valid + invalid), GET /api/repos/{id} 404
- `BotControllerTest` — CRUD endpoints
- `BenchmarkSuiteControllerTest` — create suite, add PR, remove PR
- `SetupControllerTest` — GET /api/setup/status when GitHub App not configured

## End-to-End Tests (manual — requires GitHub App configured)

### Replay Flow
1. Register an exemplar repo (a small test repo with 2–3 PRs)
2. Sync the mirror
3. Create a suite with 2 PRs
4. Create a bot pointing to the `agentic_pr_review` workflow
5. Start a run with concurrency=1
6. Verify:
   - Two replay PRs created in mirror
   - Mirror PR URLs are accessible on GitHub
   - Bot triggered on each PR (check Actions tab)
   - After bot completes: `review_comments` rows populated
   - Run status transitions: PENDING → RUNNING → COMPLETED
   - Replay PR status transitions correctly

### Grading Flow
1. Open a completed replay PR detail page
2. Grade 3 comments as VALID, 1 as INVALID
3. Verify gradings persisted
4. Promote a VALID comment to golden dataset
5. Start a second run with same suite and bot
6. Verify the comparison report shows TP=1 for the promoted issue

### Cleanup Flow
1. After a completed run, click "Cleanup Run"
2. Verify: replay PRs closed on GitHub, branches deleted from mirror
3. Verify: replay PR records still in DB (for history) but mirror PR URLs are now closed

### Golden Dataset Export/Import
1. Export golden dataset to JSON
2. Delete all entries
3. Import from the JSON file
4. Verify all entries restored with same data

## Performance Considerations

- Suite with 50 PRs × 2 bots = 100 replay PRs; test with concurrency=5
- Expect ~5-10 min for bots to complete per PR
- Total run time target: under 60 min for 100 replay PRs

## Safety Checks
- Verify exemplar repo is never modified (check git log on exemplar)
- Verify branch names in mirror follow `benchmark/{runId}/` prefix
- Verify concurrent runs for same suite don't collide (different run IDs)
- Verify canceled run stops creating new replay PRs

## Regression Tests (golden dataset)
After the first full run with known PRs is graded:
1. Export the golden dataset
2. Run the same suite again with the same bot
3. Compare: the comparison report's recall should be ≥ previous run's recall
4. Any regressions (issues now missed) should surface as false negatives
