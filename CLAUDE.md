# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project: PR Bot Benchmark

A tool for benchmarking AI-powered PR review bots. It replays historical GitHub PRs against configured bots, collects their comments, and supports human grading, golden dataset management, and reporting.

## Commands

### First-time setup
```bash
# Start Postgres
docker compose up -d

# Backend: download Gradle wrapper (requires system gradle or sdkman)
cd backend && gradle wrapper

# Frontend: install dependencies
cd frontend && npm install
```

### Backend (Java 21 / Spring Boot / Gradle)
```bash
cd backend

# Run (requires Postgres running and env vars set)
./gradlew bootRun

# Build JAR
./gradlew build

# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.prbotbenchmark.SomeTest"

# Skip tests
./gradlew build -x test
```

### Frontend (React / Vite)
```bash
cd frontend

npm run dev      # dev server on :5173, proxies /api to :8080
npm run build    # production build to dist/
npm run preview  # preview production build
```

### Required environment variables (backend)
```bash
GITHUB_APP_ID=123456
GITHUB_APP_PRIVATE_KEY_PATH=/path/to/private-key.pem
GITHUB_APP_INSTALLATION_ID=78901234
GITHUB_MIRROR_ORG=your-org-name   # org where mirror repos are created
DB_URL=jdbc:postgresql://localhost:5432/prbotbenchmark  # default
DB_USER=prbotbenchmark                                   # default
DB_PASSWORD=prbotbenchmark                               # default
WORKSPACE_BASE_PATH=~/.pr-bot-benchmark/workspaces      # default
```

## Architecture

### Monorepo structure
```
backend/   — Java 21 Spring Boot REST API (port 8080)
frontend/  — React 18 + TypeScript + Vite (port 5173 in dev)
docker-compose.yml  — Postgres 16
```

### Backend package structure (`com.prbotbenchmark`)
| Package | Responsibility |
|---|---|
| `github` | GitHub App JWT auth, REST API client, response DTOs |
| `repository` | Exemplar repo registration and mirror management |
| `benchmark` | Benchmark suites and PR selection |
| `bot` | Bot definitions (GitHub Actions workflows) |
| `runner` | Run orchestration, concurrency control, `ReplayPr` lifecycle |
| `replay` | Git workspace management + branch/PR creation in mirror |
| `collection` | Comment harvesting from GitHub, text normalization |
| `similarity` | Pluggable similarity strategies for deduplication |
| `grading` | Human/machine grading, golden dataset |
| `reporting` | Run metrics, golden dataset comparison |
| `setup` | GitHub App setup wizard API |
| `config` | Async executor, CORS, Jackson |

### Core flow
1. **Register** an exemplar repo → tool creates a mirror repo in the configured GitHub org
2. **Build a suite** by selecting PRs from the exemplar (fetched live from GitHub)
3. **Configure bots** — each bot has a GitHub Actions workflow YAML that the tool injects into the mirror
4. **Start a run** — for each `(SuitePr, Bot)` pair:
   - `GitWorkspaceService` creates a local git clone of the exemplar
   - Creates `benchmark/{runId}/{prNumber}/base` branch at the original base commit + injects workflow file via GitHub Tree API
   - Creates `benchmark/{runId}/{prNumber}/head` branch at the original head commit (pushed via `git push`)
   - Opens a PR in the mirror → bot triggers via GitHub Actions
   - `RunOrchestrator` polls until bots complete (configurable: wait for CHECKS, REVIEWS, or BOTH)
   - `CommentCollector` harvests all PR comments, review comments, and review bodies
5. **Grade** — humans label bot and original comments (VALID/INVALID/DUPLICATE/NEEDS_REVIEW), stars (0–5), flagged
6. **Promote** good comments to the **golden dataset** for future run comparison
7. **Report** — precision, recall, F1 per bot vs. golden dataset

### Replay is non-destructive
- All branches are namespaced `benchmark/{runId}/...` — different runs never collide
- Mirror repo is separate from the exemplar; exemplar is never modified
- Cleanup action closes replay PRs and deletes branches on demand

### Similarity strategies (pluggable — all are Spring `@Component` beans)
- `EXACT` — exact normalized text match
- `FILE_LINE` — same file + line number
- `NORMALIZED_TEXT` — Jaro-Winkler distance on normalized text
- `WINNOWING` — Karp-Rabin rolling hash fingerprinting (Schleimer winnowing algorithm)
- `LLM` — Ollama-compatible endpoint (disabled by default, enable via `app.llm.enabled=true`)

### Database
PostgreSQL 16. Schema managed by Flyway. JPA entities use `@Enumerated(EnumType.STRING)`. All timestamps are `Instant` (UTC).

Migrations:
- `V1__initial_schema.sql` — core tables
- `V2__webhook_events.sql` — `webhook_events` table (idempotency + webhook payload store)
- `V3__bot_versioning.sql` — `bot_snapshots` table (captures bot config at run start)
- `V4__issue_types.sql` — `issue_types` taxonomy table + seeded defaults
- `V5__scheduled_runs.sql` — `scheduled_runs` table (cron-based automation)

### GitHub App authentication
`GitHubAppAuthService` generates a short-lived JWT (RS256, 10 min) signed with the app's private key, exchanges it for an installation access token (1 hour), and caches/refreshes it. All `GitHubApiClient` calls use this token.

Required GitHub App permissions: Contents (R/W), Pull Requests (R/W), Checks (R), Workflows (R/W), Metadata (R). Events: Pull request, Pull request review, Check run, Check suite.

### Run concurrency
`RunOrchestrator` uses a `Semaphore` bounded by `run.concurrency` to control how many replay PRs are processed simultaneously. Executor pool is `replayExecutor` (max: `app.run.max-concurrency`).

### Additional features (V2+)
- **Webhooks**: `GitHubWebhookController` (POST `/api/webhooks/github`) verifies HMAC-SHA256 signature and publishes `BotCompletionEvent` to let `RunOrchestrator` react instantly rather than polling
- **Rate limiting / retry**: `GitHubApiClient` tracks `X-RateLimit-*` headers, retries 5xx/IOExceptions with exponential backoff, handles 429 via `Retry-After`. Config: `app.github.retry.*`
- **Trend reporting**: `ReportingController` exposes `/api/reports/bots/{botId}/trend` and `/api/reports/runs/{id}/significance` (McNemar's test). Frontend: `/trend` page with Recharts line chart
- **Grading queue**: `/api/grading-queue` returns ungraded comments with filter by run/suite. Bulk grading via `/api/gradings/bulk`. Frontend: `/grading-queue` page with inline and bulk grade UI
- **CSV/SARIF export**: `/api/exports/runs/{id}/csv`, `/api/exports/runs/{id}/sarif`, `/api/exports/golden-dataset/csv`
- **Bot versioning**: `BotSnapshot` captures bot name, workflow, wait strategy at the moment a run starts. Exposed via `/api/runs/{id}/bot-snapshots`
- **Issue type taxonomy**: CRUD at `/api/issue-types`. Seeded defaults (NULL_DEREF, SQL_INJECTION, etc.) via V4 migration. Frontend: `/issue-types` page
- **Scheduled runs**: `ScheduledRun` entity with cron expression (validated via Spring `CronExpression`). `ScheduledRunScheduler` fires via `@Scheduled` every minute. Manual trigger via `/api/scheduled-runs/{id}/trigger`. Frontend: `/scheduled-runs` page

## Key design decisions
- Git operations (clone, fetch, push branches) use system `git` via `ProcessBuilder` — no JGit dependency
- GitHub API called via Java 11 `HttpClient` directly (no Octokit) — keeps dependencies minimal
- Bot workflow injection uses GitHub Tree API (create blob → tree → commit → update ref) to avoid git merge complications
- Frontend proxies `/api` to `:8080` in dev via Vite config — no CORS issues in development
