# Git Primer for the PR Bot Benchmark Tool

This document explains the git concepts you need to understand how the PR Bot Benchmark tool works, walks through exactly what it does step by step, and explains why the approach is safe and non-destructive.

---

## Part 1: Git Fundamentals (The Bits You Need)

### 1. What Is a Repository?

A git repository is two things stored together in the `.git/` directory:

1. **An object database** — a content-addressed store of every file, directory snapshot, and commit that has ever existed in the repo. Objects are identified by their SHA-1 hash.
2. **A refs database** — a small set of named pointers (branches, tags, HEAD) that say "this name currently points to this SHA."

Everything else (your working directory, `git status`, the index) is built on top of these two stores.

### 2. Commits, Trees, and Blobs

Git stores three types of objects:

**Blob** — the raw content of a single file. Two files with identical content share one blob.

**Tree** — a directory snapshot. It lists filenames and points each one to either a blob (file) or another tree (subdirectory).

**Commit** — a snapshot of your whole project at one moment. It contains:
- A pointer to the root tree object (the full directory snapshot)
- A pointer to the parent commit(s)
- Author name, email, timestamp
- Commit message
- Its own SHA, which is a hash of all of the above

```
Commit abc123...
  ├── tree: 9f8e7d...        ← root directory snapshot
  ├── parent: 11223344...   ← previous commit
  ├── author: Alice <alice@acme.com>
  └── message: "Add user auth"

Tree 9f8e7d...
  ├── README.md  → blob a1b2c3...
  ├── src/       → tree 44556677...
  └── .github/   → tree 88990011...
```

Because a commit's SHA is computed from its content (including its parent pointer), changing anything about a commit produces a completely different SHA. This is what makes commits **immutable** — you cannot edit a commit in place.

### 3. Branches Are Just Pointers

This is the most important concept in this document.

A branch is nothing more than a text file containing a 40-character SHA. That's it. When you run `git checkout -b feature`, git writes one small file. When you make a new commit on that branch, git updates that file to contain the new SHA.

```
Before commit:

  main ──────────────► abc123...
                           │
                       (commit)

After new commit on main:

  main ──────────────► def456...
                           │
                       (new commit, parent: abc123...)
                           │
                           ▼
                       abc123...
```

Moving a branch pointer does not copy, move, or modify any commits. The old commit `abc123...` still exists unchanged. You have only updated a label.

This means creating a branch is cheap (one file write), deleting a branch is cheap (one file delete), and neither operation touches the underlying commit history.

### 4. SHAs as Addresses

A SHA (like `abc123def456789...`) is a deterministic fingerprint of content:

- The same content always produces the same SHA
- Different content always produces a different SHA (in practice)
- You cannot reverse a SHA to get the content without having the object stored

Because of this, SHAs are stable, globally unique identifiers. If two repos both have a commit with SHA `abc123...`, they have the same commit — same content, same parent, same everything.

### 5. Remotes

A remote is just a name that maps to a URL. When you run `git remote add origin https://github.com/acme/backend`, you are storing the string "origin points to that URL." Nothing else happens.

Operations like `git fetch origin` and `git push origin` use that URL to talk to another git server over HTTPS (or SSH). The protocol is just: "send me these objects" / "here are these objects."

You can also bypass the remote name entirely and use a URL directly:

```
git push https://github.com/acme/backend-benchmark.git main
```

This is exactly what the tool does.

### 6. `git push {url} {sha}:refs/heads/{branch}` — What This Does

This is the most precise form of a push. Breaking it down:

```
git push  https://github.com/acme/backend-benchmark.git  def456...:refs/heads/benchmark/5/47/head
          └─────────────────────────────────────────────  └───────────────────────────────────────
          where to push                                    what to push : where to put it
```

- **`def456...`** — the exact SHA of an existing commit in your local repo
- **`refs/heads/benchmark/5/47/head`** — the full ref path to create or update on the remote

The effect: the remote server receives any objects it doesn't already have (walking back through parents), then creates (or updates) the branch `benchmark/5/47/head` pointing to `def456...`.

No local branch is created. No remote tracking ref is stored locally. It's a precise, one-shot operation.

### 7. The Staging Area and Working Tree (Brief)

The **working tree** is the files on disk you edit. The **index (staging area)** is a snapshot of what your next commit will contain — `git add` moves changes from working tree into the index, `git commit` turns the index into a commit object.

The tool mostly uses the GitHub API instead of local git commands, so the staging area is rarely relevant here. The one place local git is used is pushing commits from a local workspace clone — and that operation uses SHAs directly, not the staging area.

---

## Part 2: How the Tool Uses Git — A Concrete Example

### The Setup

- **Exemplar repo**: `github.com/acme/backend`
  - PR #47: "Add user authentication"
  - Base commit (tip of `main` at PR creation time): `abc123def456...`
  - Head commit (tip of `feature/user-auth`): `def456abc789...`
  - The PR changed 3 files: `src/auth.ts`, `src/middleware.ts`, `tests/auth.test.ts`
- **Mirror repo**: `github.com/acme/backend-benchmark`
- **Bot**: `agentic-pr-review`, workflow file: `review.yml`
- **Run ID**: 5

### Step 1: Ensure the Mirror Has the Commits

The tool maintains a local workspace at `~/.pr-bot-benchmark/workspaces/acme-backend/`. This is a `git clone --no-single-branch` of the exemplar — it has all branches and all history.

To make sure the mirror has the commits it needs, the tool runs:

```
git push https://github.com/acme/backend-benchmark.git --all
```

from the workspace. This sends all commits from the exemplar to the mirror. After this, the mirror's object database contains `abc123def456...`, `def456abc789...`, and every ancestor commit.

No branches are created yet. The mirror just has the raw objects.

### Step 2: Create the Base Branch (via GitHub's Tree API)

The base branch needs to be the original base code plus the bot's workflow file — but the workflow file must not appear in the PR diff. The solution: add the workflow file in a new commit that is the *parent* of what the head branch sees as the base.

Here is what the tool does via the GitHub API (no local git involved):

**2a. Look up the tree SHA at the base commit.**

Ask GitHub: "What is the root tree object for commit `abc123def456...`?" GitHub returns something like `tree SHA = 9f8e7daa...`.

**2b. Create a blob for the workflow file.**

Upload the contents of `review.yml` to the mirror. GitHub stores it as a new blob and returns its SHA, say `blobSHA = cc99aa11...`.

**2c. Create a new tree.**

Tell GitHub: "Make a new tree that is identical to `9f8e7daa...`, but add the file `.github/workflows/bot.yml` pointing to blob `cc99aa11...`." GitHub returns `newTreeSHA = 77ff3322...`.

**2d. Create a new commit.**

Tell GitHub: "Make a new commit with tree `77ff3322...`, parent `abc123def456...`, and message 'Add benchmark workflow'." GitHub returns `newCommitSHA = ee11bb99...`.

The commit graph now looks like this:

```
  ... ── abc123def456...  ──►  ee11bb99...
         (original base)        (new commit: base + workflow)
              ▲                        ▲
         head will see           base branch points here
         this as the diff base
```

**2e. Create the base branch ref.**

Tell GitHub: "Create branch `benchmark/5/47/base` pointing to `ee11bb99...`."

```
benchmark/5/47/base ──► ee11bb99...
                              │
                          (tree: original base code + bot.yml)
                          (parent: abc123def456...)
```

### Step 3: Create the Head Branch (via git push)

The head branch should point directly at the original PR's head commit, unchanged.

From the local workspace:

```
git push https://github.com/acme/backend-benchmark.git \
  def456abc789...:refs/heads/benchmark/5/47/head
```

The mirror already has `def456abc789...` (synced in Step 1). This command just creates the branch label pointing to it. No modification to any commit.

```
benchmark/5/47/head ──► def456abc789...
                              │
                          (tree: base code + auth changes)
                          (parent: abc123def456...)
```

### Step 4: Open the Pull Request

The tool calls the GitHub API to open a PR on the mirror:

- **Title**: "PR #47: Add user authentication" (or similar)
- **Head branch**: `benchmark/5/47/head`
- **Base branch**: `benchmark/5/47/base`

### Step 5: What GitHub Actions Sees

GitHub Actions with `on: [pull_request]` checks the **base branch** for workflow files. The base branch `benchmark/5/47/base` contains `.github/workflows/bot.yml`. So GitHub Actions finds the workflow and triggers the `agentic-pr-review` bot.

The head branch `benchmark/5/47/head` does not contain the workflow file (it's a descendant of `abc123def456...`, which predates the workflow addition). So the workflow file does not appear in the PR diff.

### Step 6: What the PR Diff Shows

GitHub computes the diff as: changes between `benchmark/5/47/base` and `benchmark/5/47/head`.

The merge base of these two branches is `abc123def456...` (the original base commit, which is the parent of both `ee11bb99...` and `def456abc789...`).

```
Diff = changes from abc123def456... to def456abc789...
     = src/auth.ts (added)
     + src/middleware.ts (modified)
     + tests/auth.test.ts (added)
```

The workflow file is not in this diff because it was added on the base branch (between `abc123def456...` and `ee11bb99...`), not between the merge base and the head. The PR diff shows exactly the same changes as the original PR #47. The bot sees a clean, realistic code review situation.

### The Full Picture

```
Mirror repo: github.com/acme/backend-benchmark

Object database (after sync + setup):
  abc123def456...  (original base commit, from exemplar)
  def456abc789...  (original head commit, from exemplar)
  ee11bb99...      (new commit: base + workflow, created by tool)
  ...all ancestor commits...

Branch refs:
  benchmark/5/47/base ──► ee11bb99...
  benchmark/5/47/head ──► def456abc789...

Open PR on mirror:
  head: benchmark/5/47/head
  base: benchmark/5/47/base
  diff: src/auth.ts, src/middleware.ts, tests/auth.test.ts
  GitHub Actions: triggered (workflow found on base branch)
```

---

## Part 3: Why It Is Safe

### 1. The Exemplar Repo Is Read-Only

The tool only ever reads from the exemplar repo: listing PRs, fetching commit SHAs, fetching history into the local workspace. It never calls a write API on the exemplar. No branches are created there, no PRs are opened there, no pushes are made to it.

The local workspace is cloned from the exemplar, but pushes go from the workspace to the mirror — never back to the exemplar.

### 2. The Mirror Is Fully Isolated

The mirror is a separate repo with no star/watch/fork relationship to the exemplar. All writes happen only in the mirror. Even if something went wrong — an accidental force push, a naming collision, a corrupted state — it would only affect the mirror, which is a benchmark-only repo with no production use.

### 3. Branches Are Namespaced by Run ID

Every branch follows the pattern `benchmark/{runId}/{prNumber}/{base|head}`. The `runId` is the database ID of the `BenchmarkRun` record, which is unique per run.

This gives a mathematical guarantee: two runs cannot produce the same branch name even if they cover the same PRs.

```
Run 1 covers PR #42:  benchmark/1/42/base,  benchmark/1/42/head
Run 2 covers PR #42:  benchmark/2/42/base,  benchmark/2/42/head
```

These are four distinct refs. They can coexist in the same repo without interference.

### 4. Commits Are Immutable

The tool creates new commits (for the base branch, adding the workflow file). It never amends existing commits, never rewrites history, and never uses `--force` on any push.

If a push fails because a branch already exists (for example, from a previous failed run), the tool surfaces that error rather than silently overwriting. You know something is wrong instead of having a corrupted state.

### 5. The Head Branch Is a Direct, Unmodified Pointer

Pushing `def456abc789...:refs/heads/benchmark/5/47/head` creates a new branch name that points to an existing, already-committed, already-immutable object. The commit is not touched. Its SHA does not change. Its content does not change. You are only creating a label.

The bot sees exactly the same head commit SHA as the original PR had.

### 6. Cleanup Is Explicit and Reversible

Branches and PRs persist until you request cleanup. This is intentional. At any point after a benchmark run, you can:

- Open the mirror on GitHub and browse the replay PRs
- See exactly what code the bot was shown
- Read the bot's comments
- Verify the diff matches the original

When you do request cleanup, the tool closes the PRs and deletes the `benchmark/...` branches via the GitHub API. Database records are preserved for history. The mirror repo itself is not deleted — only the benchmark branches for that run.

### 7. Access Token Scope

The GitHub App token has access only to the repos where the app is installed. It cannot read from or write to unrelated repos, even if someone constructed a request to try. The exemplar and mirror must both have the app installed, and scope is limited to those two repos.

### 8. No Force Pushes Anywhere

The tool never uses `--force` or `--force-with-lease`. Every push is a simple, non-destructive ref creation. If a ref already exists and the push would overwrite it, git will reject the push with an error. The tool surfaces that error. Nothing is silently overwritten.

---

## Part 4: Common Questions

**What if I run the same benchmark suite twice? Will branches conflict?**

No. Each run gets a new `runId` from the database. Run 1 produces `benchmark/1/47/base`; run 2 produces `benchmark/2/47/base`. They are distinct refs and can coexist in the mirror without any conflict.

**What if the mirror repo doesn't exist yet?**

The tool creates it automatically before doing anything else. It uses the GitHub API to create a new empty repo under the configured organization.

**Can I delete the mirror repo?**

Yes. If you delete it, the tool will recreate it on the next sync. Because the tool syncs commits from the exemplar before creating branches, the mirror will be fully populated again. Previous benchmark run records in the database will have stale PR links (since those PRs are gone), but no data is lost on the exemplar or in the database.

**What commits exist in the mirror after a run?**

After a sync and a run:
- All commits from the exemplar (synced via `git push --all`)
- The new base-branch commits created by the tool (one per benchmark PR, per run) — these are the commits that add the workflow file

The mirror ends up as a superset of the exemplar's history plus the tool's generated commits.

**Does the diff in the replay PR exactly match the original PR's diff?**

For the changed files: yes. The head branch points to the exact same commit SHA as the original PR's head. The merge base is the original base commit `abc123def456...`, which is the parent of both the base branch commit and the head branch commit.

The one difference: the base branch has one extra commit (adding the workflow file) that does not appear in the PR diff. From the reviewer's (or bot's) perspective, the diff is identical to the original.

**What if a bot modifies the PR or closes it?**

The tool only reads comments from the replay PR — it collects bot responses and stores them. It does not depend on the PR remaining open or unmodified. Most bots only post review comments and do not close PRs. If a bot does close a PR or add commits, the tool records whatever comments exist and moves on. Cleanup still works: the tool calls the close API regardless of current state, and branch deletion is independent of PR state.

**What if the workflow file path conflicts with a file that already exists in the repo?**

The path used is `.github/workflows/bot.yml` (or similar). This path almost never appears in application PR diffs, since workflow changes are typically made in separate PRs. If it does appear, the tool is adding the workflow to the base branch — not the head branch — so the workflow file will not show in the PR diff regardless. The base branch commit adds the file, and that commit is the parent of what both branches diverge from, so it's excluded from the diff computation.
