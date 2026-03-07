# PR Bot Benchmark — User Guide

## Table of Contents

1. [Introduction](#1-introduction)
2. [Prerequisites](#2-prerequisites)
3. [Quick Start](#3-quick-start)
4. [Setting Up Your GitHub App](#4-setting-up-your-github-app)
5. [Registering Your First Repository](#5-registering-your-first-repository)
6. [Creating a Benchmark Suite](#6-creating-a-benchmark-suite)
7. [Selecting PRs for Your Suite](#7-selecting-prs-for-your-suite)
8. [Configuring Bots](#8-configuring-bots)
9. [Running a Benchmark](#9-running-a-benchmark)
10. [Monitoring Progress](#10-monitoring-progress)
11. [Grading Bot Comments](#11-grading-bot-comments)
12. [The Golden Dataset](#12-the-golden-dataset)
13. [Understanding Reports](#13-understanding-reports)
14. [Cleanup](#14-cleanup)
15. [Troubleshooting](#15-troubleshooting)
16. [Reference: Grading Verdicts and Severity](#16-reference-grading-verdicts-and-severity)
17. [Reference: Bot Wait Strategies](#17-reference-bot-wait-strategies)

---

## 1. Introduction

PR Bot Benchmark is a tool for systematically evaluating AI-powered pull request review bots. Instead of deploying a bot to production and hoping for the best, you can replay real historical pull requests against your bot candidates, collect their reviews, grade the comments against human judgment, and track how each bot's performance changes over time.

### What it does

- **Replays historical PRs**: Takes real PRs from a source repository and creates synthetic copies in a controlled mirror repository. Bots review the synthetic copies, leaving your production code completely untouched.
- **Collects bot output**: Waits for each registered bot to finish reviewing, then harvests all comments — inline, general, and review body.
- **Supports human grading**: Reviewers assess each bot comment with a structured verdict (valid, invalid, duplicate, needs review), severity, and star rating.
- **Maintains a golden dataset**: Curated, agreed-upon issues can be promoted to a golden dataset that serves as ground truth. Future runs are compared against this dataset to produce precision, recall, and F1 scores.
- **Tracks performance over time**: Run the same benchmark suite against updated bot configurations and compare reports to see whether quality improved or regressed.

### Who it is for

Engineering teams that are evaluating or developing AI PR review bots and want objective, reproducible quality measurements rather than anecdotal impressions.

---

## 2. Prerequisites

Before you begin, you need:

- **A GitHub organization** where you have permission to install GitHub Apps and create repositories. A personal account works, but an org is recommended for team use.
- **The PR Bot Benchmark server** running locally or on a server. The API is available at `http://localhost:8080/api` and the UI at `http://localhost:5173`.
- **At least one source ("exemplar") GitHub repository** containing merged pull requests with real code changes. The richer and more representative the PRs, the better your benchmark will be.
- **A bot workflow** — a GitHub Actions workflow YAML file that performs PR review. This is the bot you want to evaluate.

> **Note**: You do not need direct GitHub API tokens. All GitHub access is handled through the GitHub App you create during setup.

---

## 3. Quick Start

If you want to get to your first benchmark result as fast as possible, follow these steps. Each step links to the detailed section for more context.

1. Open the UI at `http://localhost:5173` and click **Setup Wizard** in the sidebar.
2. Follow the wizard to create and install a GitHub App in your organization. ([Details](#4-setting-up-your-github-app))
3. Go to **Repositories** and register your exemplar repo. Click **Sync Mirror**. ([Details](#5-registering-your-first-repository))
4. Go to **Suites** and click **New Suite**. Give it a name like `my-first-suite`. ([Details](#6-creating-a-benchmark-suite))
5. Open the suite and use the PR picker to select 3–5 representative PRs. ([Details](#7-selecting-prs-for-your-suite))
6. Go to **Bots** and register your bot by pasting its workflow YAML. ([Details](#8-configuring-bots))
7. Go back to your suite and click **Start Run**. Select your bot and leave concurrency at 2. ([Details](#9-running-a-benchmark))
8. Watch the **Run Detail** page as replay PRs are created and bot comments arrive. ([Details](#10-monitoring-progress))
9. Click into any replay PR to grade the bot's comments. ([Details](#11-grading-bot-comments))
10. Promote good comments to the **Golden Dataset**. ([Details](#12-the-golden-dataset))
11. Open **Reports** to see your bot's precision and recall scores. ([Details](#13-understanding-reports))

---

## 4. Setting Up Your GitHub App

PR Bot Benchmark uses a GitHub App for all interactions with GitHub. The app needs read/write access to repositories so it can create mirror repos, push branches, open pull requests, and read bot comments.

### Why a GitHub App?

GitHub Apps authenticate as an installation (not as a user), which means:
- Actions are attributed to the app, not to your personal account.
- Fine-grained permissions limit exactly what the tool can touch.
- You can install the app on a specific organization rather than your entire account.

### Step-by-step: Using the Setup Wizard

The Setup Wizard walks you through creating the app. Open it from the sidebar.

**Step 1 — Name your app**

Enter a name for the app, for example `pr-bot-benchmark`. The name must be unique across all of GitHub, so add a suffix if needed (e.g., `pr-bot-benchmark-acme`).

**Step 2 — Generate the manifest**

The wizard generates a GitHub App manifest with all required permissions pre-filled. Click **Create on GitHub**. You will be redirected to GitHub.

**Step 3 — Confirm on GitHub**

On GitHub, review the app details and click **Create GitHub App for [your org]**. GitHub will redirect back to the wizard with your new app's credentials.

**Step 4 — Install the app**

After the app is created, click **Install App** and choose your organization. Select **All repositories** or, if you prefer, choose specific repositories — at minimum, the mirror repository that will be created during setup.

> **Tip**: Installing on "All repositories" is easiest for getting started. You can tighten the scope later from the app's installation settings on GitHub.

**Step 5 — Verify**

Go to **Settings** in the sidebar. You should see your GitHub App listed as connected with a green status indicator. If it shows an error, see [Troubleshooting](#15-troubleshooting).

### Required GitHub App permissions

| Permission | Level | Why |
|---|---|---|
| Contents | Read & Write | Push branches and commits to the mirror repo |
| Pull requests | Read & Write | Open, close, and read PR comments |
| Checks | Read | Detect when bot check runs have finished |
| Workflows | Read & Write | Inject bot workflow files into branches |
| Metadata | Read | Required by GitHub for all apps |

### Required webhook events

| Event | Why |
|---|---|
| Pull request | Detect when replay PRs are opened/closed |
| Pull request review | Capture bot review submissions |
| Check run | Detect when bot check runs complete |
| Check suite | Group check runs for a PR |

---

## 5. Registering Your First Repository

An **exemplar repo** is the real GitHub repository whose pull request history you want to benchmark against. PR Bot Benchmark reads its PRs but never modifies it.

### Register the repo

1. Go to **Repositories** in the sidebar.
2. Click **Register Repository**.
3. Enter the repository in `owner/repo` format, for example:

   ```
   acme-corp/payments-service
   ```

4. Click **Save**.

The repository appears in the list with status "No mirror yet."

### Sync the mirror

The **mirror** is a copy of the repository that lives in your GitHub organization. All synthetic replay PRs are created here.

1. Click **Sync Mirror** next to your repository.
2. The tool creates a new repository named something like `benchmark-mirror-payments-service` in your org, then copies the default branch's contents.

> **Warning**: Syncing the mirror for a large repository (thousands of commits) can take several minutes. The UI shows sync status — wait for it to complete before proceeding.

> **Warning**: The exemplar repository is never modified. It is only read. All writes go to the mirror.

---

## 6. Creating a Benchmark Suite

A **benchmark suite** is a named collection of pull requests from one exemplar repository. You can have multiple suites for the same repo — for example, one suite for "bug fix PRs" and another for "large refactors."

### Create a suite

1. Go to **Suites** in the sidebar.
2. Click **New Suite**.
3. Fill in:
   - **Name**: A short, descriptive name, e.g., `payments-service-bugfixes`
   - **Description** (optional): Notes about what these PRs represent
   - **Repository**: Select the exemplar repo you registered
4. Click **Create Suite**.

The suite starts empty. The next step is to pick which PRs go into it.

---

## 7. Selecting PRs for Your Suite

Open a suite to see the **PR picker** — a live list of pull requests fetched directly from the exemplar repository on GitHub.

### Using the PR picker

The picker shows closed (merged) PRs from the exemplar repo, ordered by most recent first. Each row shows the PR number, title, author, and merge date.

- **Search**: Type in the search box to filter by title or PR number.
- **Select**: Click the checkbox next to any PR to add it to the suite.
- **Review**: Selected PRs appear in the **Suite PRs** list below the picker.

### Choosing good PRs

The quality of your benchmark depends heavily on which PRs you select. Good candidates are:

- **Representative**: Cover the kinds of changes your bot is likely to review in production.
- **Non-trivial**: Have real diffs, not just formatting changes or single-line fixes.
- **Varied**: Mix of file types, change sizes, and domains (backend, frontend, tests, configs).
- **Historical**: Older PRs are safer — they have stable diffs and won't confuse bots with stale context.

A suite of 5–20 PRs is enough to get meaningful signal. More PRs improve statistical confidence but take longer to run.

> **Tip**: Start small. 5 PRs is enough to validate your setup and grading workflow before committing to a large benchmark.

---

## 8. Configuring Bots

A **bot** in PR Bot Benchmark is a GitHub Actions workflow that reviews pull requests. You register bots by providing their workflow YAML content.

### How bots are triggered

When a benchmark run creates a replay PR in the mirror repository, the tool injects the bot's workflow file into the base branch of that PR. When the PR is opened, GitHub Actions detects the new pull request and triggers the workflow automatically.

This means your bot workflow must be triggered by the `pull_request` event and should post its output as PR comments or a pull request review.

### Register a bot

1. Go to **Bots** in the sidebar.
2. Click **Register Bot**.
3. Fill in:
   - **Name**: A human-readable name, e.g., `gpt-4o-reviewer-v2`
   - **Workflow filename**: The filename GitHub Actions will use, e.g., `pr-review.yml`
   - **Workflow content**: Paste the full YAML content of the workflow
   - **Wait strategy**: How to detect when the bot has finished (see [Reference: Bot Wait Strategies](#17-reference-bot-wait-strategies))
   - **Timeout (seconds)**: How long to wait before giving up and moving on (default: 600)

### Example workflow YAML

Here is a minimal example of a bot workflow that posts a review using a Python script:

```yaml
name: AI PR Reviewer

on:
  pull_request:
    types: [opened, synchronize]

jobs:
  review:
    runs-on: ubuntu-latest
    permissions:
      pull-requests: write
      contents: read
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: Run AI review
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
        run: |
          pip install openai pygithub
          python review.py \
            --repo "${{ github.repository }}" \
            --pr "${{ github.event.pull_request.number }}"
```

> **Warning**: The workflow runs in the mirror repository, not the exemplar repo. Make sure any secrets the bot needs (like `OPENAI_API_KEY`) are configured in the mirror repository's GitHub Actions secrets, or at the organization level.

### Wait strategy selection guide

| Your bot posts output as... | Recommended wait strategy |
|---|---|
| A pull request review (Approve/Request Changes/Comment) | `REVIEWS` |
| A check run with pass/fail | `CHECKS` |
| Both a review and check runs | `BOTH` |
| PR comments only, no check run | `REVIEWS` (if it uses the review API) or `CHECKS` (if it also creates a check) |

---

## 9. Running a Benchmark

With a suite configured and at least one bot registered, you are ready to run a benchmark.

### Start a run

1. Open a suite (go to **Suites**, click the suite name).
2. Verify the PR list looks correct.
3. Click **Start Run**.
4. In the dialog:
   - **Select bots**: Check one or more bots to include in this run. You can benchmark multiple bots simultaneously.
   - **Concurrency**: How many replay PRs to create in parallel (default: 2). Higher values finish faster but may hit GitHub API rate limits on busy organizations.
5. Click **Start**.

You are automatically redirected to the **Run Detail** page.

### What happens next

For each PR in the suite, and for each selected bot, the tool:

1. Creates a `benchmark/{runId}/{prNumber}/base` branch in the mirror repo with the same base commit as the original PR.
2. Creates a `benchmark/{runId}/{prNumber}/head` branch with the diff applied on top.
3. Injects the bot's workflow file into the base branch.
4. Opens a pull request from head to base in the mirror repo.
5. Waits for the bot to run (according to the wait strategy and timeout).
6. Collects all comments left by the bot.
7. Closes the replay PR (but does not delete branches — see [Cleanup](#14-cleanup)).

Multiple PRs are processed concurrently up to the configured concurrency limit.

> **Tip**: Run IDs are unique and stable. Branches are namespaced under the run ID, so multiple runs never interfere with each other, even when they target the same PRs.

---

## 10. Monitoring Progress

The **Run Detail** page shows everything happening in a run.

### Progress overview

At the top, a summary bar shows:
- Total replay PRs
- Completed / in-progress / queued / failed counts
- Elapsed time

The page auto-refreshes every few seconds while the run is active.

### Replay PR table

Each row in the table represents one (original PR, bot) combination and shows:
- Original PR number and title (links to the exemplar repo)
- Replay PR link (links to the actual PR in the mirror repo — you can watch the bot commenting in real time)
- Bot name
- Status: `queued`, `in_progress`, `completed`, `timed_out`, `failed`
- Comment count once collected

### Cancel and cleanup

- **Cancel Run**: Stops processing any new replay PRs. Already-running replay PRs finish naturally.
- **Cleanup**: Closes any open replay PRs in the mirror repo and optionally deletes the benchmark branches. Use this when you are done with the run and want to keep the mirror repo tidy.

> **Warning**: Cleanup is always on-demand and never runs automatically. This ensures you have time to inspect the replay PRs directly on GitHub before they are removed.

---

## 11. Grading Bot Comments

Grading is the human review step. You look at each comment the bot made and assess whether it identified a real issue.

### Navigate to grading

From the Run Detail page, click on any completed replay PR row to open the **Replay PR Detail** page.

### What you see

The page is split into two columns:

- **Left — Bot comments**: Every comment the bot posted on the replay PR, including inline comments (with file path and line number), general PR comments, and the review body if it submitted a formal review.
- **Right — Original human comments**: The comments from the original PR's human reviewers, for reference.

Each bot comment shows:
- Comment body (rendered markdown)
- Type badge: `inline`, `general`, or `review-body`
- For inline comments: the file path and line number

### Grading a comment

Click the grading form beneath a comment to expand it. Fill in:

- **Verdict** (required): See [Reference: Grading Verdicts and Severity](#16-reference-grading-verdicts-and-severity)
- **Severity** (optional, relevant for VALID comments): How important is this issue?
- **Stars** (0–5): A freeform quality signal. Use 5 for an exceptionally well-explained, high-value comment; use 1 for technically valid but poorly phrased.
- **Flagged**: Toggle this to mark a comment that needs more discussion before you can decide.
- **Notes**: Free text for your reasoning, context, or disagreements.

Click **Save** to record the grading.

> **Tip**: You can save multiple gradings on the same comment — for example, one from a human and one from an automated grading pass. Each grading is independent.

### Promoting to golden

When a comment receives a `VALID` verdict, a **Promote to Golden** button appears. Clicking it opens a form to capture the canonical version of the issue for the golden dataset:

- **File path** (pre-filled from the comment if it is inline)
- **Line number** (pre-filled)
- **Issue type**: A short category label, e.g., `null-check`, `off-by-one`, `sql-injection`
- **Description**: A concise description of the issue independent of the specific code
- **Canonical body**: The ideal way a reviewer would phrase this finding

---

## 12. The Golden Dataset

The **golden dataset** is a curated set of real issues, promoted from graded comments, that serves as the ground truth for evaluating future bot runs.

### Browsing the dataset

Go to **Golden Dataset** in the sidebar. Each entry shows:
- File path and line number (if inline)
- Issue type and description
- Canonical body
- Which exemplar PR it came from
- Two toggles: **Active** and **Included by Default**

### Toggles explained

| Toggle | Effect |
|---|---|
| **Active** | When on, this entry is included in precision/recall calculations for future runs. Turn it off to exclude an entry without deleting it — useful for entries you are unsure about. |
| **Included by Default** | When on, new benchmark runs automatically include this entry in their comparison. Turn it off for entries that are only relevant to specific bot configurations. |

### Export and import

- **Export**: Downloads the full dataset as `golden-dataset.json`. Use this to back up your dataset, share it with teammates, or version-control it.
- **Import**: Accepts a JSON file upload or pasted JSON text. Importing merges new entries with existing ones — it does not replace the dataset.

The JSON format is a flat array of golden dataset entries. Each entry has fields matching the form fields above plus a stable `id`.

---

## 13. Understanding Reports

Go to **Reports** in the sidebar and select a bot to see its performance summary.

### Comment volume stats

The first section shows aggregate counts across all runs for the selected bot:
- Total comments posted
- Breakdown by type: inline, general, review-body

This tells you whether a bot is chatty or terse, and whether it tends to leave inline comments on specific lines or general PR-level observations.

### Grading breakdown

A bar chart shows how comments were graded across all runs:
- **Valid**: Identified a real issue
- **Invalid**: Hallucinated or irrelevant
- **Duplicate**: Found the same issue another comment already found
- **Needs Review**: A human left the verdict unresolved
- **Ungraded**: Not yet assessed

A high valid-to-invalid ratio is a strong signal. A large "ungraded" bar means your team has pending work before the reports are meaningful.

### Comparison vs golden dataset

This section shows the bot's performance against the golden dataset. For each run (or aggregated across runs), you see:

| Metric | Meaning |
|---|---|
| **True Positives** | Golden issues the bot correctly flagged |
| **False Negatives** | Golden issues the bot missed entirely |
| **False Positives** | Comments the bot made that do not match any golden issue |
| **Precision** | TP / (TP + FP) — what fraction of the bot's comments were correct |
| **Recall** | TP / (TP + FN) — what fraction of real issues the bot found |
| **F1** | Harmonic mean of precision and recall — the overall quality score |

The bar chart (powered by Recharts) shows these metrics across multiple runs, making it easy to see trends.

### Interpreting the numbers

A well-performing bot should have:
- **High recall** (finds most real issues) — more important than precision for a security-critical context
- **High precision** (does not waste reviewer time on false positives) — more important when reviewer attention is scarce
- **F1 above 0.6** is a reasonable baseline for a useful bot; above 0.8 is excellent

> **Tip**: Low precision + low recall usually means the bot is not well-calibrated to your codebase. Consider fine-tuning with examples from your golden dataset. Low recall + decent precision means the bot is conservative — it finds real issues but misses many.

---

## 14. Cleanup

Benchmark runs leave branches and (optionally) closed PRs in the mirror repository. PR Bot Benchmark never deletes these automatically — you are in full control.

### When to clean up

- After you have finished grading all comments in a run.
- Before archiving an old run you no longer need.
- To keep the mirror repository tidy.

### How to clean up

1. Open the **Run Detail** page for the run.
2. Click **Cleanup**.
3. Confirm the action.

Cleanup closes any still-open replay PRs in the mirror repo and deletes the `benchmark/{runId}/*` branches.

> **Warning**: Cleanup is irreversible. Once branches are deleted, the replay PRs cannot be re-inspected on GitHub (though all collected comments remain in the PR Bot Benchmark database).

> **Tip**: You can clean up runs one at a time or in bulk from the Runs list page.

---

## 15. Troubleshooting

### GitHub App not connecting

**Symptom**: The Settings page shows the GitHub App as disconnected or erroring.

**Checks**:
1. Verify the app was installed on the correct organization. Go to your org's Settings > GitHub Apps and confirm the app is listed as installed.
2. Confirm the app has the required permissions (see [Section 4](#4-setting-up-your-github-app)). If you changed permissions after initial creation, you need to re-install the app for the new permissions to take effect.
3. Check the server logs for authentication errors. A common cause is that the private key on the server does not match the app's current private key on GitHub. Regenerate the private key on GitHub and update the server configuration.
4. Make sure the webhook events are correctly configured on the app. Go to the app's settings on GitHub and verify the events listed in [Section 4](#4-setting-up-your-github-app) are checked.

### Bots not triggering

**Symptom**: Replay PRs are created but the bot never posts any comments. The replay PR status stays `in_progress` until timeout.

**Checks**:
1. Open the replay PR link on GitHub and click the **Checks** tab. If no checks appear at all, GitHub Actions did not pick up the workflow.
2. Verify the workflow file was injected into the base branch. On GitHub, browse the base branch (`benchmark/{runId}/{prNumber}/base`) and check for the workflow file at `.github/workflows/{workflowFileName}`.
3. Check whether the GitHub App has **Workflows: Read & Write** permission. Without it, the tool cannot push workflow files.
4. Check the **Actions** tab on the mirror repository. If workflows are disabled for the repo, you need to enable them in the repository settings.
5. Make sure the workflow's `on:` trigger matches `pull_request`. If it is scoped to specific branches or paths, the replay PR's branches may not match.
6. If the workflow requires secrets (e.g., an OpenAI API key), confirm those secrets are configured in the mirror repository or at the org level.

### Run stuck / replay PRs not completing

**Symptom**: A run has been going for a long time and replay PR statuses are not changing.

**Checks**:
1. Check the **timeout** setting on the bot configuration. If the bot is slow, increase the timeout (e.g., to 900 or 1200 seconds).
2. Open the replay PR on GitHub and check the **Checks** tab. If checks are still running, the bot is just slow — wait or increase the timeout.
3. If checks show as failed, the bot workflow errored out. Check the workflow run logs on GitHub for the root cause (missing secrets, code errors, API quota exceeded).
4. Check the wait strategy. If it is set to `BOTH`, both a passing check run and at least one review must be present. If the bot only does one of these, switch to the appropriate single strategy (`CHECKS` or `REVIEWS`).
5. If the server process was restarted mid-run, in-flight replay PRs may be orphaned. Use the **Cancel Run** button and start a new run for the affected PRs.

### Mirror sync failing

**Symptom**: Clicking **Sync Mirror** shows an error.

**Checks**:
1. Confirm the exemplar repository exists and is accessible to the GitHub App installation.
2. Confirm the GitHub App is installed on the organization where the mirror will be created (not just on the exemplar repo's org, if they are different).
3. Check whether a mirror repo with the expected name already exists but was deleted and recreated manually. If so, delete the orphaned entry in the Repositories page and re-register.

---

## 16. Reference: Grading Verdicts and Severity

### Verdicts

| Verdict | When to use |
|---|---|
| `VALID` | The bot identified a real issue in the code. The comment is correct and would be useful to a PR author. |
| `INVALID` | The bot hallucinated, made a factual error, or the comment is irrelevant to the actual code. |
| `DUPLICATE` | The bot identified a real issue, but another comment on the same PR already flagged the same problem. Use this to avoid double-counting in metrics. |
| `NEEDS_REVIEW` | You cannot decide without more context, discussion, or expertise. Use this as a placeholder — unresolved NEEDS_REVIEW comments are visible in reports as a reminder. |

### Severity (applies to VALID comments)

| Severity | When to use |
|---|---|
| `CRITICAL` | A bug or vulnerability that must be fixed before merge. Data loss, security hole, crash. |
| `MAJOR` | A significant issue that should be fixed but might not block merge in all contexts. Logic error, performance cliff, bad API contract. |
| `MINOR` | A real but small issue. Wrong variable name, missing null check in a non-critical path, suboptimal but functional code. |
| `NITPICK` | Style, formatting, or preference. Correct either way. Would not warrant a blocking review on its own. |

### Stars (0–5)

Stars are a freeform quality signal for ranking comments within the VALID set:

| Stars | Meaning |
|---|---|
| 5 | Exceptional: correctly identified a critical bug with a clear, well-explained comment |
| 4 | Very good: correct finding, well explained |
| 3 | Good: correct finding, adequate explanation |
| 2 | Fair: correct finding, but explanation is unclear or misleading |
| 1 | Poor: technically correct but barely useful |
| 0 | Not yet rated |

Stars do not affect precision/recall calculations. They are useful for identifying your best examples to add to fine-tuning datasets.

### Flagged

Toggle **Flagged** on a comment to mark it for follow-up — for example, when two reviewers disagree on the verdict, or when you want a domain expert to weigh in before finalizing the grade. Flagged comments appear highlighted in the grading UI and can be filtered in the comment list.

---

## 17. Reference: Bot Wait Strategies

The wait strategy tells PR Bot Benchmark when to consider a bot "done" and proceed to collect its comments.

### `CHECKS`

Waits until all check runs on the replay PR reach a terminal state (`success`, `failure`, `cancelled`, `skipped`). Use this when your bot workflow:
- Creates an explicit check run (via the Checks API)
- Or uses a GitHub Actions job that produces a check run automatically (which all jobs do)

This is the most reliable strategy because GitHub Actions job completion is always reflected as a check run status change.

### `REVIEWS`

Waits until at least one pull request review has been submitted on the replay PR. Use this when your bot:
- Calls the GitHub Reviews API to submit a formal review (`Approve`, `Request Changes`, or `Comment`)
- And you want to collect comments before the check run has fully settled (e.g., if the check suite takes a long time to close out)

> **Warning**: If your bot posts inline comments without submitting a formal review, `REVIEWS` will never trigger. Make sure the workflow actually calls the reviews endpoint, not just the PR comments endpoint.

### `BOTH`

Waits for both conditions: all check runs complete AND at least one review is submitted. Use this for maximum confidence that the bot has truly finished all its work. This is the safest choice if your bot does both and you want to ensure no late comments are missed.

### Timeout

Regardless of wait strategy, the benchmark moves on after `timeoutSeconds` (default: 600 seconds / 10 minutes). When a timeout occurs:
- The replay PR status is set to `timed_out`
- Any comments posted up to the timeout point are still collected
- The run continues with remaining replay PRs

If timeouts are common, increase the value when registering or editing the bot. 900–1200 seconds is reasonable for slower AI review bots that call external APIs with latency.
