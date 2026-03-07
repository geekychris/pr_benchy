# PR Bot Benchmark

A tool for evaluating and comparing AI-powered PR review bots. Replays historical GitHub PRs against configured bots, collects their reviews, supports human grading, and tracks performance over time against a golden dataset.

## Quick Start

### Prerequisites
- Java 21
- Node 18+
- Docker (for Postgres)
- A GitHub App (see Setup)

### 1. Start the database
```bash
docker compose up -d
```

### 2. Configure the backend
Create a `.env` file or export these variables:
```bash
export GITHUB_APP_ID=your-app-id
export GITHUB_APP_PRIVATE_KEY_PATH=/path/to/private-key.pem
export GITHUB_APP_INSTALLATION_ID=your-installation-id
export GITHUB_MIRROR_ORG=your-github-org
```

### 3. Run the backend
```bash
cd backend
gradle wrapper   # first time only
./gradlew bootRun
```

### 4. Run the frontend
```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173

## GitHub App Setup

Go to **Settings → Developer Settings → GitHub Apps → New GitHub App** in your GitHub account/org.

**Required Repository Permissions:**
- Contents: Read & Write
- Pull requests: Read & Write
- Checks: Read
- Workflows: Read & Write
- Metadata: Read (required by GitHub)

**Subscribe to Events:**
- Pull request
- Pull request review
- Check run
- Check suite

After creation:
1. Generate and download a private key (`.pem` file)
2. Note your **App ID** from the app settings page
3. Install the app on your organization
4. Note the **Installation ID** from the installation URL

The app's Setup Wizard (`/setup`) walks through this with exact instructions.

## How It Works

1. **Register an exemplar repo** — a GitHub repo containing PRs you want to benchmark against
2. **Create a benchmark suite** — select which PRs from the exemplar to include
3. **Configure bots** — each bot is a GitHub Actions workflow file that will be injected into replay PRs
4. **Start a run** — the tool clones the exemplar, creates synthetic PRs in a mirror repo, waits for bots to review them, and collects all comments
5. **Grade results** — human reviewers label bot comments (Valid/Invalid/Duplicate) with optional severity, stars, and flags
6. **Golden dataset** — promote agreed-upon issues to a golden dataset; future runs are measured against it (precision/recall/F1)

## Architecture

- **Backend**: Java 21, Spring Boot 3, PostgreSQL, Flyway
- **Frontend**: React 18, TypeScript, Vite, Ant Design 5
- **GitHub integration**: GitHub App (JWT + installation tokens)
- **Git operations**: system `git` via ProcessBuilder (no JGit)

See [CLAUDE.md](./CLAUDE.md) for detailed architecture documentation.
