# PR Bot Benchmark — Architecture & Design Documentation

## Table of Contents

1. [System Overview](#1-system-overview)
2. [Architecture Overview](#2-architecture-overview)
3. [Backend Architecture](#3-backend-architecture)
4. [Database Design](#4-database-design)
5. [GitHub Integration](#5-github-integration)
6. [The Replay Engine](#6-the-replay-engine)
7. [Concurrency Model](#7-concurrency-model)
8. [Similarity & Deduplication](#8-similarity--deduplication)
9. [Grading & Golden Dataset](#9-grading--golden-dataset)
10. [Frontend Architecture](#10-frontend-architecture)
11. [Key Design Decisions](#11-key-design-decisions)
12. [Configuration Reference](#12-configuration-reference)
13. [Extension Points](#13-extension-points)

---

## 1. System Overview

PR Bot Benchmark is a developer tool for **evaluating the quality of AI-powered GitHub pull request review bots**. Given a set of real, historical GitHub PRs (the "exemplar" corpus), the system replays each PR as a synthetic PR in a controlled mirror repository, waits for bots to review it via GitHub Actions, collects their comments, and compares the results against human-curated ground truth.

### Who it is for

- Teams that build or operate AI PR review bots and want to track quality regressions or improvements over time.
- Researchers benchmarking different LLM-backed review strategies against the same corpus.
- Engineering managers who need objective metrics (precision, recall, F1) on bot review quality before deploying changes.

### What it does

1. **Ingests exemplar repositories** — real GitHub repos containing PRs worth reviewing.
2. **Builds benchmark suites** — curated selections of PRs from exemplar repos that represent the desired test distribution.
3. **Defines bots** — a bot is a GitHub Actions workflow YAML file plus a wait strategy. Any CI-triggered review tool can be wrapped as a bot.
4. **Runs benchmarks** — for each (suite PR, bot) pair the system creates a synthetic "replay" PR in a mirror repo, triggers the bot's workflow, and waits for completion.
5. **Collects comments** — harvests inline review comments, PR review bodies, and general PR comments from each replay PR.
6. **Grades comments** — human reviewers (or automated rules) label collected bot comments as VALID, INVALID, DUPLICATE, or NEEDS_REVIEW.
7. **Maintains a golden dataset** — approved comments are promoted to a ground truth dataset representing "issues that should be found."
8. **Generates reports** — per-run metrics and cross-run comparisons (TP/FP/FN, precision, recall, F1 per bot).

---

## 2. Architecture Overview

```
  ┌─────────────────────────────────────────────────────────────────┐
  │                        Browser (React 18)                       │
  │  Repos │ Suites │ Bots │ Runs │ Grading │ Reports │ Setup       │
  └──────────────────────────┬──────────────────────────────────────┘
                             │ HTTP (Vite proxy → :8080)
  ┌──────────────────────────▼──────────────────────────────────────┐
  │                   Spring Boot 3.3  (:8080)                      │
  │                                                                  │
  │  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌────────────────┐  │
  │  │ REST API │  │ Run      │  │ Replay   │  │ GitHub API     │  │
  │  │ Layer    │  │ Orches-  │  │ Engine   │  │ Client         │  │
  │  │(Controllers)│ trator  │  │          │  │(Java HttpClient)│  │
  │  └────┬─────┘  └────┬─────┘  └────┬─────┘  └───────┬────────┘  │
  │       │             │             │                 │            │
  │  ┌────▼─────────────▼─────────────▼─────────────────▼────────┐  │
  │  │                     Service Layer                          │  │
  │  │  BenchmarkSuite │ BenchmarkRun │ Bot │ Grading │ Report   │  │
  │  └────────────────────────────┬───────────────────────────────┘  │
  │                               │                                  │
  │  ┌────────────────────────────▼───────────────────────────────┐  │
  │  │              Spring Data JPA  (Hibernate)                  │  │
  │  └────────────────────────────┬───────────────────────────────┘  │
  └───────────────────────────────┼──────────────────────────────────┘
                                  │ JDBC
  ┌───────────────────────────────▼──────────────────────────────────┐
  │                    PostgreSQL 16                                  │
  │  exemplar_repos │ benchmark_suites │ bots │ benchmark_runs       │
  │  replay_prs │ review_comments │ gradings │ golden_dataset_entries│
  └──────────────────────────────────────────────────────────────────┘

  ┌──────────────────────────────────────────────────────────────────┐
  │                       GitHub.com                                 │
  │                                                                  │
  │  Exemplar Repos (read-only)    Mirror Repos (created by tool)   │
  │  ┌──────────────────────┐      ┌──────────────────────────────┐ │
  │  │ owner/original-repo  │      │ mirror-owner/mirror-repo     │ │
  │  │  PR #42 (historical) │      │  PR benchmark/run1/42/base   │ │
  │  └──────────────────────┘      │    ← bot workflow runs here  │ │
  │                                └──────────────────────────────┘ │
  └──────────────────────────────────────────────────────────────────┘
```

### Data flow summary

```
Exemplar PRs  →  Benchmark Suite  →  Benchmark Run
                                          │
                              ┌───────────▼────────────┐
                              │  For each (PR × Bot):  │
                              │  1. Create replay PR   │
                              │  2. Wait for bot CI    │
                              │  3. Collect comments   │
                              └───────────┬────────────┘
                                          │
                              ┌───────────▼────────────┐
                              │  Grade comments        │
                              │  Promote to golden set │
                              │  Generate reports      │
                              └────────────────────────┘
```

---

## 3. Backend Architecture

### Technology stack

| Concern | Choice |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3 |
| Build | Gradle (Kotlin DSL) |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| ORM | Spring Data JPA / Hibernate |
| HTTP client | Java 11 `HttpClient` (no external HTTP library) |
| Git operations | System `git` via `ProcessBuilder` |
| Async | Spring `@Async` + `CompletableFuture` + `Semaphore` |

### Package structure

All backend code lives under `com.prbotbenchmark`. Each sub-package represents a bounded domain:

```
com.prbotbenchmark/
├── github/          GitHub App JWT auth, REST API client, response DTOs
├── repository/      ExemplarRepo CRUD, mirror sync lifecycle
├── benchmark/       BenchmarkSuite + SuitePr selection
├── bot/             Bot definitions (workflow YAML, wait strategy, timeout)
├── runner/          BenchmarkRun lifecycle, RunOrchestrator (async), ReplayPr status
├── replay/          GitWorkspaceService (local git clone), ReplayEngine
├── collection/      CommentCollector (GitHub API), TextNormalizer
├── similarity/      SimilarityStrategy interface + 5 implementations
├── grading/         Grading, GoldenDatasetEntry, import/export
├── reporting/       RunReport, ComparisonReport (TP/FP/FN/precision/recall/F1)
├── setup/           GitHub App setup wizard API
└── config/          Async executors, CORS, Jackson
```

### Layering

```
  REST Controllers  (thin — delegate immediately to services)
        │
  Service Layer     (orchestration, transaction boundaries, business logic)
        │
  Repository Layer  (Spring Data JPA interfaces)
        │
  Domain Entities   (JPA @Entity, lifecycle logic lives in services not entities)
```

Supporting cross-cutting components:

- `github/` — used by `replay/`, `collection/`, `runner/`, `repository/`; not a layer but a utility module
- `config/` — Spring bean configuration, not a layer

### Key patterns

**Services as the single source of truth for state transitions.** `BenchmarkRunService` owns all state machine transitions for a run (PENDING → RUNNING → COMPLETED/FAILED/CANCELLED). No entity method directly mutates status.

**Async orchestration via `RunOrchestrator`.** The orchestrator is annotated `@Async("runExecutor")` so the HTTP request that starts a run returns immediately. All heavy work (git ops, GitHub API calls, polling) happens on a dedicated thread pool.

**Spring-managed strategy lists.** The `List<SimilarityStrategy>` is injected by Spring, which collects all beans implementing the interface. Adding a new strategy requires only implementing the interface and annotating the class `@Component` — zero wiring changes elsewhere.

**ProcessBuilder for git.** Git operations that require auth (push) or that JGit handles poorly are delegated to the system `git` binary. `GitWorkspaceService` constructs authenticated remote URLs with the installation access token embedded (`https://x-access-token:{token}@github.com/...`) and executes git subprocesses, capturing stdout/stderr for error reporting.

---

## 4. Database Design

### Schema diagram

```
exemplar_repos
  id, owner, repo_name, mirror_owner, mirror_repo_name,
  github_url, description, last_synced_at
    │
    │ 1:N
    ▼
suite_prs ──────────────────────── benchmark_suites
  id, suite_id, exemplar_repo_id,    id, name, description
  pr_number, title, base_sha,
  head_sha, base_branch, head_branch
    │
    │ 1:N
    ▼
replay_prs ──────────────── benchmark_runs ──── run_bots ──── bots
  id, run_id, suite_pr_id,   id, suite_id,       run_id,       id, name,
  bot_id, mirror_pr_number,  config (JSON),      bot_id        workflow_yaml,
  mirror_pr_url, status,     status, started_at,               workflow_filename,
  created_at, completed_at   concurrency,                      wait_strategy,
    │                        poll_interval_secs                timeout_minutes
    │
    ├── 1:N ──► review_comments
    │             id, replay_pr_id, type (REVIEW_COMMENT/REVIEW/PR_COMMENT),
    │             body, normalized_body, fingerprint, file_path, line,
    │             github_comment_id, created_at
    │
    └── (suite_pr_id) → original_comments
                          id, suite_pr_id, body, normalized_body,
                          file_path, line, github_comment_id

gradings
  id, comment_id, verdict (VALID/INVALID/DUPLICATE/NEEDS_REVIEW),
  grader, notes, created_at

golden_dataset_entries
  id, suite_pr_id, body, normalized_body, file_path, line,
  source_comment_id, active, created_at

comment_similarities
  id, comment_a_id, comment_b_id, strategy, score, created_at
```

### Key schema decisions

**`replay_prs` is the central join table.** Every significant entity — comments, gradings, similarity scores — anchors to a `replay_pr`. This keeps queries focused: "all comments for this run" = join `replay_prs` on `run_id` then `review_comments` on `replay_pr_id`.

**`original_comments` is separate from `review_comments`.** Original human comments from the exemplar PRs serve as reference material during grading but are not mixed into the review comment table. This prevents accidental inclusion in metrics.

**`golden_dataset_entries` is denormalized by design.** A golden entry stores its own `body`, `file_path`, and `line` rather than just a foreign key to a comment. This is intentional: the golden dataset must remain stable even if comments are deleted or re-collected. The `source_comment_id` FK is nullable and advisory only.

**`comment_similarities` is computed, not canonical.** Similarity scores are stored as a cache after computation. They can be recomputed at any time by re-running the similarity pipeline. The `strategy` column records which algorithm produced each row.

**`benchmark_runs` stores config as JSON.** Per-run configuration details beyond the normalized fields are stored in a JSON column, making it easy to add configuration fields without schema migrations.

**Flyway migrations, not Hibernate auto-ddl.** The `spring.jpa.hibernate.ddl-auto` is set to `validate`. Every schema change is expressed as a numbered Flyway migration script (`V{n}__{description}.sql`). This ensures the schema is reproducible, auditable, and safe to apply in production.

---

## 5. GitHub Integration

### GitHub App authentication flow

The system authenticates as a GitHub App, not as a user. This gives it a stable identity, fine-grained repository permissions, and a higher API rate limit.

```
  PEM private key (stored in config)
        │
        ▼
  JWT (RS256)
    iss  = app_id
    iat  = now
    exp  = now + 10 minutes
        │
        ▼  POST /app/installations/{installationId}/access_tokens
        │
        ▼
  Installation Access Token
    token  = "ghs_..."
    expires_at = now + 1 hour
        │
        ▼  Cached in GitHubAppAuthService (ReentrantLock)
        │  Refreshed 60 seconds before expiry
        │
        ▼  Bearer {token} on all GitHubApiClient requests
```

**Thread safety.** `GitHubAppAuthService` uses a `ReentrantLock` around token retrieval. Only one thread at a time executes the refresh path; others wait on the lock and benefit from the freshly obtained token.

**Token lifetime.** GitHub installation tokens expire after one hour. The 60-second early refresh window prevents in-flight API calls from racing against expiry.

### API client design

`GitHubApiClient` wraps Java 11's `java.net.http.HttpClient`. It is a single class responsible for:

- Attaching the `Authorization: Bearer {token}` header to every request.
- JSON serialization/deserialization via Jackson (shared `ObjectMapper` from Spring context).
- Throwing typed exceptions on non-2xx responses, preserving the GitHub error body.
- All response types are plain Java records/classes in the `github/` package (no third-party GitHub SDK).

This design avoids dependency on the GitHub API SDK (which is large and has its own HTTP client) and keeps the dependency surface small.

### API usage patterns

| Operation | Endpoint | Notes |
|---|---|---|
| Get commit tree SHA | `GET /repos/{owner}/{repo}/git/commits/{sha}` | Used to build base branch |
| Create blob | `POST /repos/{owner}/{repo}/git/blobs` | Uploads workflow YAML |
| Create tree | `POST /repos/{owner}/{repo}/git/trees` | Adds workflow to base tree |
| Create commit | `POST /repos/{owner}/{repo}/git/commits` | New commit with workflow |
| Create ref (branch) | `POST /repos/{owner}/{repo}/git/refs` | Creates base branch |
| Create pull request | `POST /repos/{owner}/{repo}/pulls` | Opens replay PR |
| List check runs | `GET /repos/{owner}/{repo}/commits/{ref}/check-runs` | CHECKS wait strategy |
| List reviews | `GET /repos/{owner}/{repo}/pulls/{pr}/reviews` | REVIEWS wait strategy |
| List review comments | `GET /repos/{owner}/{repo}/pulls/{pr}/comments` | Comment collection |
| List issue comments | `GET /repos/{owner}/{repo}/issues/{pr}/comments` | Comment collection |
| Close pull request | `PATCH /repos/{owner}/{repo}/pulls/{pr}` | Cleanup |
| Delete branch | `DELETE /repos/{owner}/{repo}/git/refs/heads/{branch}` | Cleanup |
| Create repository | `POST /user/repos` or `POST /orgs/{org}/repos` | Mirror creation |

### Rate limiting

The system does not implement rate limit back-off by default. Practical mitigations:

- GitHub App installation tokens have 5,000 requests/hour, which is sufficient for most benchmark runs.
- The concurrency semaphore limits the number of simultaneous replay PRP operations, indirectly limiting API request bursts.
- For large-scale runs, reduce `app.benchmark.concurrency` in configuration.

A future improvement would be to inspect the `X-RateLimit-Remaining` and `X-RateLimit-Reset` response headers and insert a sleep when the budget is low.

---

## 6. The Replay Engine

The replay engine is responsible for materializing one synthetic PR in a mirror repository for a given (suite PR, bot) pair. It is the most complex component in the system.

### Sequence diagram

```
RunOrchestrator          ReplayEngine           GitWorkspaceService     GitHub API
      │                       │                         │                    │
      │ processReplayPr(rpr)  │                         │                    │
      │──────────────────────►│                         │                    │
      │                       │ ensureMirrorRepoExists  │                    │
      │                       │────────────────────────►│                    │
      │                       │                         │ GET /repos/{mirror}│
      │                       │                         │───────────────────►│
      │                       │                         │  404 → create repo │
      │                       │                         │◄───────────────────│
      │                       │◄────────────────────────│                    │
      │                       │                         │                    │
      │                       │─── determineMirrorCoords ───────────────────►│
      │                       │                         │                    │
      │                       │  [Base branch creation via Tree API]         │
      │                       │ GET /git/commits/{baseCommitSha}             │
      │                       │─────────────────────────────────────────────►│
      │                       │◄── {treeSha} ───────────────────────────────│
      │                       │                         │                    │
      │                       │ POST /git/blobs (workflow YAML)              │
      │                       │─────────────────────────────────────────────►│
      │                       │◄── {blobSha} ───────────────────────────────│
      │                       │                         │                    │
      │                       │ POST /git/trees (treeSha + blobSha)          │
      │                       │─────────────────────────────────────────────►│
      │                       │◄── {newTreeSha} ────────────────────────────│
      │                       │                         │                    │
      │                       │ POST /git/commits (newTreeSha, baseCommitSha)│
      │                       │─────────────────────────────────────────────►│
      │                       │◄── {baseHeadSha} ───────────────────────────│
      │                       │                         │                    │
      │                       │ POST /git/refs (benchmark/{run}/{pr}/base)   │
      │                       │─────────────────────────────────────────────►│
      │                       │◄────────────────────────────────────────────│
      │                       │                         │                    │
      │                       │  [Head branch via system git]                │
      │                       │ git push {mirrorAuthUrl} {headSha}:refs/...  │
      │                       │────────────────────────►│                    │
      │                       │                         │── git push ───────►│
      │                       │                         │◄──────────────────│
      │                       │◄────────────────────────│                    │
      │                       │                         │                    │
      │                       │ POST /pulls (head→base)                      │
      │                       │─────────────────────────────────────────────►│
      │                       │◄── {prNumber, prUrl} ───────────────────────│
      │                       │                         │                    │
      │ update ReplayPr entity│                         │                    │
      │◄──────────────────────│                         │                    │
      │                       │                         │                    │
      │  [GitHub Actions automatically triggers on base branch push]         │
      │                       │                         │                    │
      │ waitForBots(replayPr) │                         │                    │
      │──────────────────────►│                         │                    │
      │     [poll every 30s]  │                         │                    │
      │                       │ GET check-runs / reviews                     │
      │                       │─────────────────────────────────────────────►│
      │                       │◄────────────────────────────────────────────│
      │                       │  (repeat until completed or timeout)         │
      │◄──────────────────────│                         │                    │
```

### Branch naming convention

```
benchmark/{runId}/{prNumber}/base   — base branch (contains workflow file)
benchmark/{runId}/{prNumber}/head   — head branch (original PR head commit)
```

The base branch HEAD is one commit ahead of the original `baseCommitSha`. The single extra commit adds `.github/workflows/{filename}` containing the bot's workflow YAML. This makes the workflow available to GitHub Actions when the PR is opened against this branch.

### Mirror repo determination

The mirror repo coordinates are resolved in order:

1. If `exemplarRepo.mirrorOwner` and `exemplarRepo.mirrorRepoName` are set, use them.
2. Otherwise, derive from the exemplar: `mirrorOwner = exemplarRepo.owner`, `mirrorRepoName = exemplarRepo.repoName + "-benchmark-mirror"`.

The mirror repo is created as a private repo the first time it is needed. Subsequent runs reuse the same mirror.

### Wait strategies

| Strategy | Condition to proceed |
|---|---|
| `CHECKS` | All check runs on the head commit have `status == "completed"` |
| `REVIEWS` | At least one PR review has been submitted (any state) |
| `BOTH` | Both CHECKS and REVIEWS conditions are satisfied |

Timeout is non-fatal: if the bot does not complete within `timeoutMinutes`, the system logs a warning and proceeds to comment collection. This is intentional — partial results are better than no results.

### Comment collection

`CommentCollector.collectComments(replayPr)` harvests three comment types from the mirror PR:

```
Mirror PR
  ├── Inline review comments  GET /pulls/{pr}/comments
  │     → stored as CommentType.REVIEW_COMMENT
  │     → has file_path + line number
  │
  ├── Review bodies           GET /pulls/{pr}/reviews
  │     → stored as CommentType.REVIEW (where body is non-blank)
  │     → no file/line association
  │
  └── General PR comments     GET /issues/{pr}/comments
        → stored as CommentType.PR_COMMENT
        → no file/line association
```

Each comment body is processed by `TextNormalizer` (strips extra whitespace, normalizes unicode, lowercases for comparison purposes) and fingerprinted by `WinnowingHashStrategy.computeFingerprint` for deduplication.

---

## 7. Concurrency Model

### Thread pools

Two dedicated thread pools are configured in `config/`:

| Pool | Bean name | Purpose |
|---|---|---|
| `runExecutor` | `@Async("runExecutor")` | One thread per benchmark run (the orchestrator) |
| `replayExecutor` | Injected into `RunOrchestrator` | Worker threads for individual replay PRP tasks |

The `runExecutor` is a small pool (typically 4–8 threads) because runs are orchestrators, not workers. The `replayExecutor` is larger, sized to the maximum expected concurrent replay operations.

### Orchestration flow

```
HTTP POST /api/runs  →  BenchmarkRunService.createAndStart()
                              │
                              │ @Async("runExecutor")
                              ▼
                    RunOrchestrator.startRunAsync()
                              │
                              │ for each (suitePr × bot):
                              │   create ReplayPr entity (PENDING)
                              │
                              │ Semaphore(run.concurrency)
                              │
                              │ CompletableFuture.allOf(
                              │   for each replayPr:
                              │     CompletableFuture.runAsync(() -> {
                              │       semaphore.acquire()
                              │       try {
                              │         processReplayPr(replayPr)
                              │       } finally {
                              │         semaphore.release()
                              │       }
                              │     }, replayExecutor)
                              │ ).join()
                              │
                              ▼
                    mark run COMPLETED or FAILED
```

### Why `Semaphore` rather than a bounded thread pool?

A bounded `ThreadPoolExecutor` would also limit concurrency, but the `CompletableFuture.allOf(...).join()` pattern requires all futures to be submitted up front. Submitting more futures than pool threads would cause queue build-up without the Semaphore's back-pressure at the API/git call level. The Semaphore gives precise control: exactly `run.concurrency` replay tasks are in-flight at any moment, regardless of thread pool sizing.

### Cancellation

Cancellation is cooperative. Setting `run.status = CANCELLED` in the database is checked at the start of each `processReplayPr` iteration. In-flight tasks complete normally; cancellation takes effect for tasks not yet started. This avoids `Thread.interrupt()` complexity with subprocess management.

---

## 8. Similarity & Deduplication

### Purpose

Similarity strategies answer two questions:

1. **Deduplication**: is this bot comment a near-duplicate of another comment on the same PR?
2. **Golden dataset matching**: does this bot comment correspond to a known issue in the golden dataset?

### Strategy interface

```java
public interface SimilarityStrategy {
    String getName();
    double score(String textA, String textB, SimilarityContext context);
    boolean matches(String textA, String textB, SimilarityContext context);
}
```

`SimilarityContext` carries optional metadata: file path, line number, run configuration. Strategies may use or ignore context fields.

### Implementations

#### EXACT

Compares normalized text for equality. Score is 1.0 (match) or 0.0 (no match). Used as a fast pre-filter before more expensive strategies.

#### FILE_LINE

Returns 1.0 if both `filePath` and `line` are identical in the context. No text comparison. Used to detect comments pointing at the same location — a strong signal for the same issue regardless of wording.

#### NORMALIZED_TEXT

Applies Jaro-Winkler string similarity to normalized comment bodies. Configurable threshold (default 0.85). Effective for comments that describe the same issue with minor wording differences (e.g., "null check missing" vs "missing null check").

```
score = jaroWinkler(normalize(textA), normalize(textB))
matches = score >= app.similarity.normalized-text.threshold
```

#### WINNOWING

Implements the Winnowing algorithm (Schleimer, Wilkerson, Aiken 2003) for document fingerprinting:

```
text → n-grams (k=5) → rolling Karp-Rabin hash per n-gram
     → sliding window (w=4) → select minimum hash in each window
     → deduplicate adjacent identical hashes
     → fingerprint = Set<Long>

similarity(A, B) = |fingerprint(A) ∩ fingerprint(B)| / |fingerprint(A) ∪ fingerprint(B)|
                 = Jaccard similarity on selected hash sets
```

This is robust against minor edits, whitespace changes, and comment reformatting. `computeFingerprint` is also used to generate the `fingerprint` column stored on every `review_comment` row.

#### LLM

Calls an Ollama-compatible embedding or completion endpoint to determine semantic similarity. Disabled by default (`@ConditionalOnProperty("app.similarity.llm.enabled")`). When enabled, it sends both comment texts in a prompt and parses a similarity score from the response.

This is the most expensive strategy (network call per pair) and is intended for use in offline golden dataset comparison, not in the real-time collection pipeline.

### Pluggability

Spring collects all `SimilarityStrategy` beans into `List<SimilarityStrategy>` at startup. The active strategies for a given operation are controlled by `app.similarity.default-strategies` (a CSV of strategy names). The system looks up strategies by name from the injected list, making it easy to add, remove, or reorder strategies without code changes.

### Configuration

```yaml
app:
  similarity:
    default-strategies: WINNOWING,NORMALIZED_TEXT
    normalized-text:
      threshold: 0.85
    winnowing:
      k: 5        # n-gram size
      w: 4        # window size
    llm:
      enabled: false
      endpoint: http://localhost:11434
      model: llama3
```

---

## 9. Grading & Golden Dataset

### Flow

```
ReplayPr comments
      │
      │ CommentCollector harvests
      ▼
review_comments (raw bot output)
      │
      │ Human reviewer opens Grading UI
      ▼
gradings (VALID / INVALID / DUPLICATE / NEEDS_REVIEW)
      │
      │ For VALID comments: "Promote to golden dataset"
      ▼
golden_dataset_entries (active=true)
      │
      │ Future benchmark runs compare against
      ▼
ComparisonReport: TP / FP / FN → precision / recall / F1
```

### Grading verdicts

| Verdict | Meaning |
|---|---|
| `VALID` | The bot correctly identified a real issue |
| `INVALID` | The bot comment is wrong, noise, or not actionable |
| `DUPLICATE` | The bot raised the same issue multiple times on the same PR |
| `NEEDS_REVIEW` | Grader is unsure; needs a second opinion |

### Golden dataset

The golden dataset is the set of `golden_dataset_entries` with `active = true`. Each entry represents a known issue that a correct bot should find. Entries are created by promoting a VALID-graded comment via `POST /api/golden-dataset/promote`.

**Matching logic.** When generating a `ComparisonReport`, each bot comment on a replay PR is matched against the golden entries for that suite PR using:

- File path + line equality (`FILE_LINE` strategy), OR
- Normalized body substring containment

A match is a true positive (TP). A golden entry with no matching bot comment is a false negative (FN). A bot comment with no matching golden entry is a false positive (FP).

**Import/export.** The golden dataset can be exported as JSON (`GET /api/golden-dataset/export`) and re-imported (`POST /api/golden-dataset/import`). This enables sharing ground truth across teams or seeding a fresh installation.

**Stability guarantee.** Golden entries store their own body/file/line and are not deleted when comments are re-collected. The `active` flag allows retiring entries without deletion, preserving historical audit trails.

### Reports

`RunReport` — per-run summary:
- Total replay PRs created
- Status breakdown (COMPLETED, FAILED, TIMED_OUT)
- Total comments collected, per bot
- Grading breakdown

`ComparisonReport` — quality metrics per bot for a run:
- True positives, false positives, false negatives (vs golden dataset)
- Precision = TP / (TP + FP)
- Recall = TP / (TP + FN)
- F1 = 2 × (precision × recall) / (precision + recall)

---

## 10. Frontend Architecture

### Technology stack

| Concern | Choice |
|---|---|
| Framework | React 18 |
| Language | TypeScript |
| Build tool | Vite |
| UI components | Ant Design 5 |
| Data fetching | React Query (TanStack Query) |
| Charts | Recharts |
| API proxy | Vite dev server proxy → `:8080` |

### Page structure

```
App
├── /repos                  Exemplar repository list + add repo
│   └── /repos/{id}         Repo detail: sync status, PR list
├── /suites                 Benchmark suite list + create suite
│   └── /suites/{id}        Suite detail: add/remove PRs
├── /bots                   Bot list + create/edit bot
├── /runs                   Run list + start new run
│   └── /runs/{id}          Run detail: live progress, replay PR list
│       └── /runs/{id}/replay-prs/{rpid}  Comment viewer + grading
├── /golden-dataset         Golden dataset browser + import/export
├── /reports
│   └── /reports/runs/{id}  Per-run report with charts
│   └── /reports/compare    Cross-run comparison
└── /setup                  GitHub App setup wizard
```

### React Query patterns

All server state is managed by React Query. The key patterns used:

**List queries with stale-while-revalidate:**
```typescript
const { data: runs } = useQuery({
  queryKey: ['runs'],
  queryFn: () => api.getRuns(),
  staleTime: 10_000,
})
```

**Live progress polling for active runs:**
```typescript
const { data: progress } = useQuery({
  queryKey: ['run', id, 'progress'],
  queryFn: () => api.getRunProgress(id),
  refetchInterval: run?.status === 'RUNNING' ? 5000 : false,
})
```

**Mutations with optimistic cache invalidation:**
```typescript
const mutation = useMutation({
  mutationFn: api.createGrading,
  onSuccess: () => queryClient.invalidateQueries({ queryKey: ['comments', replayPrId] }),
})
```

### API proxy

The Vite dev server is configured to proxy `/api/*` to `http://localhost:8080`. In production, the built React app is served as static files from the Spring Boot server (placed in `src/main/resources/static`), so no proxy is needed.

```typescript
// vite.config.ts
export default {
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
}
```

### Key frontend components

- **RunProgress** — polling dashboard showing per-bot completion bars, live comment counts.
- **CommentGrader** — side-by-side view of bot comment vs original comments with verdict buttons.
- **SimilarityMatrix** — Recharts heatmap of pairwise comment similarity scores.
- **ComparisonChart** — Recharts bar chart of precision/recall/F1 across bots.
- **SetupWizard** — step-by-step GitHub App installation guide driven by the `/api/setup/*` endpoints.

---

## 11. Key Design Decisions

### System git over JGit

**Decision:** Use `ProcessBuilder` to invoke the system `git` binary rather than the JGit library.

**Rationale:** The primary operation requiring this choice is `git push` with authenticated URLs. JGit's credential management is complex and does not natively support the `https://x-access-token:{token}@github.com/...` URL pattern reliably. System git handles this URL form universally, is well-understood by developers, and matches what they would run on the command line when debugging.

**Tradeoffs:** Subprocess management introduces PATH dependency, output parsing, and process lifecycle concerns. Unit testing requires mocking the process or using integration tests with local bare repos. The codebase mitigates this by centralizing all git subprocess calls in `GitWorkspaceService`.

### GitHub Tree API for base branch creation

**Decision:** Use the GitHub Git Data API (blobs/trees/commits/refs) to create the base branch rather than a local git checkout.

**Rationale:** The base branch must contain the bot's workflow YAML at `.github/workflows/{filename}`. This file does not exist in the original repo. Adding it via the Tree API avoids needing a local checkout of potentially gigabytes of repository history. It creates a clean commit graph: `baseCommitSha` → one new commit (adding only the workflow file) = base branch HEAD. There are no merge conflicts, no local disk space requirements, and no local git state to clean up.

**Tradeoffs:** The Tree API is verbose (4 API calls for one logical operation: blob, tree, commit, ref). A helper method in `ReplayEngine` encapsulates this sequence.

### Separate mirror repository, not forks

**Decision:** Create a fresh private GitHub repository as the mirror rather than forking the exemplar.

**Rationale:**
- A fork creates a permanent relationship with the original repo visible on GitHub.
- Forks inherit open issues/PRs from the original, contaminating the mirror PR list.
- A private mirror is fully under the operator's control: it can be deleted, renamed, or have collaborators added without any effect on the original.
- Multiple benchmark runs using the same mirror work cleanly because branch names encode the run ID.

### Polling over webhooks for bot completion detection

**Decision:** Poll the GitHub Check Runs and Reviews APIs on a timer rather than receiving webhook events.

**Rationale:** Webhooks require a publicly routable URL. This tool is designed to run locally or in a private environment. Polling is self-contained, requires no inbound network configuration, handles retries naturally (next poll picks up the state), and is easier to reason about. Bot runs take minutes, so the 30-second polling interval introduces negligible latency.

**Tradeoffs:** Polling consumes API quota and adds up to `pollIntervalSeconds` of latency in detecting completion. For high-volume runs with many simultaneous replay PRs, this could become significant. A future improvement is webhook support as an optional fast path.

### Flyway over Hibernate auto-DDL

**Decision:** Use Flyway for all schema changes with `ddl-auto: validate`.

**Rationale:** `ddl-auto: create` or `update` are convenient in development but unsafe in production — they can silently drop columns or fail on constraint violations. Flyway migrations are versioned SQL files in source control. Every schema state is reproducible, every change is auditable, and the migration can be reviewed before it runs in production.

### Non-fatal timeout in polling

**Decision:** Timeout during bot completion polling is a warning, not an error. The run proceeds to comment collection.

**Rationale:** A bot that partially completes (e.g., posts some comments but its CI check hangs) should still have its posted comments collected. Marking the entire replay PR as failed due to a hung check run would discard valid data. The timeout is recorded in the `replay_prs` status (`TIMED_OUT`) so it is visible in reports.

### Golden dataset denormalization

**Decision:** Golden dataset entries store their own body/file/line rather than referencing the source comment by FK.

**Rationale:** The golden dataset must be stable. If a comment is deleted, re-normalized, or re-collected with a different ID, the golden entry should remain valid. The advisory `source_comment_id` FK enables auditing without creating a hard dependency. Export/import of the golden dataset is also simpler because it is self-contained.

---

## 12. Configuration Reference

All properties are under the `app` namespace in `application.yml`.

### GitHub App

```yaml
app:
  github:
    app-id: 123456                          # GitHub App numeric ID
    private-key-path: /path/to/private.pem  # Path to RS256 PEM private key
    installation-id: 78901234               # Installation ID for the target account/org
    api-base-url: https://api.github.com    # Override for GitHub Enterprise
```

### Benchmark runner

```yaml
app:
  benchmark:
    concurrency: 4          # Max simultaneous replay PR operations (Semaphore limit)
    poll-interval-seconds: 30  # How often to check bot completion
    default-timeout-minutes: 60  # Per-replay-PR timeout (non-fatal)
```

### Git workspace

```yaml
app:
  git:
    workspace-dir: /tmp/pr-bot-benchmark  # Local directory for git clone operations
    cleanup-on-complete: true             # Delete workspace after push
```

### Similarity

```yaml
app:
  similarity:
    default-strategies: WINNOWING,NORMALIZED_TEXT  # CSV of strategy names
    normalized-text:
      threshold: 0.85       # Jaro-Winkler threshold for a match
    winnowing:
      k: 5                  # n-gram size for Winnowing
      w: 4                  # window size for Winnowing
    llm:
      enabled: false        # Enable LLM strategy (@ConditionalOnProperty)
      endpoint: http://localhost:11434  # Ollama-compatible endpoint
      model: llama3         # Model name
      timeout-seconds: 30
```

### Database

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/prbotbenchmark
    username: prbotbenchmark
    password: secret
  jpa:
    hibernate:
      ddl-auto: validate    # Never change this to create/update in production
  flyway:
    enabled: true
    locations: classpath:db/migration
```

### Async executors

```yaml
app:
  async:
    run-executor:
      core-pool-size: 4
      max-pool-size: 8
      queue-capacity: 100
    replay-executor:
      core-pool-size: 8
      max-pool-size: 20
      queue-capacity: 200
```

### CORS

```yaml
app:
  cors:
    allowed-origins: http://localhost:5173  # Vite dev server origin
```

---

## 13. Extension Points

### Adding a new similarity strategy

1. Create a class in `com.prbotbenchmark.similarity` implementing `SimilarityStrategy`.
2. Annotate it `@Component` (or `@ConditionalOnProperty` if it requires external config).
3. Implement `getName()` to return a unique string identifier (e.g., `"SEMANTIC_HASH"`).
4. Implement `score()` and `matches()`.

Example skeleton:

```java
@Component
public class SemanticHashStrategy implements SimilarityStrategy {

    @Override
    public String getName() { return "SEMANTIC_HASH"; }

    @Override
    public double score(String textA, String textB, SimilarityContext ctx) {
        // compute and return 0.0–1.0
    }

    @Override
    public boolean matches(String textA, String textB, SimilarityContext ctx) {
        return score(textA, textB, ctx) >= threshold;
    }
}
```

No other wiring is needed. Spring injects all `SimilarityStrategy` beans into `List<SimilarityStrategy>` wherever that list is consumed. The new strategy becomes selectable by name in `app.similarity.default-strategies`.

To make the strategy configurable from the API (e.g., selectable per benchmark run), add its name to the `SimilarityStrategyName` enum if one exists, or ensure `getName()` matches the string clients pass.

### Adding a new bot wait strategy

1. Add a value to the `WaitStrategy` enum in `com.prbotbenchmark.bot`.
2. Implement the polling condition in `RunOrchestrator.waitForBots()` — add a case to the strategy switch.
3. Add the new strategy to bot configuration UI (frontend `BotForm` component).

The polling loop, timeout handling, and error recovery are centralized in `waitForBots()` and do not need to change.

### Adding a new comment type

1. Add a value to the `CommentType` enum in `com.prbotbenchmark.collection`.
2. Implement a harvesting method in `CommentCollector` that calls the appropriate GitHub API endpoint and maps the response to `ReviewComment` entities with the new type.
3. Call the new method from `collectComments()`.
4. Update `TextNormalizer` if the new comment type requires different normalization (e.g., stripping code fences from a new source).

### Adding a new report metric

1. Add computation logic in `com.prbotbenchmark.reporting.ComparisonReportService` (or create a new service).
2. Add the metric field to `ComparisonReport` DTO.
3. Expose it via `GET /api/reports/runs/{id}/comparison`.
4. Render it in the frontend `ComparisonChart` component.

Metrics that require new database columns should be implemented as computed values in the service layer first. Only persist pre-computed values if query performance becomes a concern.

### Adding a new exemplar repository source (e.g., GitLab)

The `github/` package currently hard-codes GitHub API URLs. To support an alternative VCS:

1. Extract `GitHubApiClient` behind a `VcsApiClient` interface covering the operations used by `ReplayEngine` and `CommentCollector`.
2. Implement `GitLabApiClient` (or similar) implementing the same interface.
3. Add a `vcs_type` discriminator to `exemplar_repos`.
4. In `ReplayEngine`, select the appropriate client based on `exemplarRepo.vcsType`.

This is a significant refactor but the seams are well-defined: `ReplayEngine` depends only on the API client interface, not on GitHub-specific types.
