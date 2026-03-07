-- Exemplar repositories (original repos with PRs to benchmark against)
CREATE TABLE exemplar_repos (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(255) NOT NULL,
    github_url        VARCHAR(500) NOT NULL,
    owner             VARCHAR(255) NOT NULL,
    repo_name         VARCHAR(255) NOT NULL,
    mirror_org        VARCHAR(255),
    mirror_repo_name  VARCHAR(255),
    mirror_repo_url   VARCHAR(500),
    default_branch    VARCHAR(255) NOT NULL DEFAULT 'main',
    last_synced_at    TIMESTAMPTZ,
    sync_status       VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    config_json       JSONB,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (owner, repo_name)
);

-- Benchmark suites: named sets of PRs to benchmark
CREATE TABLE benchmark_suites (
    id                      BIGSERIAL PRIMARY KEY,
    name                    VARCHAR(255) NOT NULL,
    description             TEXT,
    exemplar_repo_id        BIGINT       NOT NULL REFERENCES exemplar_repos (id),
    pr_selection_config_json JSONB,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Individual PRs selected for a benchmark suite
CREATE TABLE suite_prs (
    id                  BIGSERIAL PRIMARY KEY,
    suite_id            BIGINT      NOT NULL REFERENCES benchmark_suites (id) ON DELETE CASCADE,
    original_pr_number  INTEGER     NOT NULL,
    title               VARCHAR(500),
    description         TEXT,
    base_commit_sha     VARCHAR(40),
    head_commit_sha     VARCHAR(40),
    base_branch         VARCHAR(255),
    head_branch         VARCHAR(255),
    author              VARCHAR(255),
    merged_at           TIMESTAMPTZ,
    files_changed       INTEGER,
    additions           INTEGER,
    deletions           INTEGER,
    pr_labels           TEXT[],
    metadata_json       JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (suite_id, original_pr_number)
);

-- Bot definitions (GitHub Actions-based reviewers)
CREATE TABLE bots (
    id                   BIGSERIAL PRIMARY KEY,
    name                 VARCHAR(255) NOT NULL UNIQUE,
    description          TEXT,
    workflow_file_name   VARCHAR(255),
    workflow_content     TEXT,
    wait_strategy        VARCHAR(50)  NOT NULL DEFAULT 'BOTH',
    timeout_seconds      INTEGER      NOT NULL DEFAULT 600,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Benchmark runs: an execution of a suite against selected bots
CREATE TABLE benchmark_runs (
    id                      BIGSERIAL PRIMARY KEY,
    suite_id                BIGINT      NOT NULL REFERENCES benchmark_suites (id),
    name                    VARCHAR(255) NOT NULL,
    status                  VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    concurrency             INTEGER      NOT NULL DEFAULT 2,
    golden_dataset_enabled  BOOLEAN      NOT NULL DEFAULT TRUE,
    started_at              TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    error_message           TEXT,
    config_json             JSONB,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Many-to-many: which bots are used in a run
CREATE TABLE run_bots (
    run_id  BIGINT NOT NULL REFERENCES benchmark_runs (id) ON DELETE CASCADE,
    bot_id  BIGINT NOT NULL REFERENCES bots (id),
    PRIMARY KEY (run_id, bot_id)
);

-- Replay PRs: synthetic PRs created in the mirror for each (suite_pr, bot) pair
CREATE TABLE replay_prs (
    id               BIGSERIAL PRIMARY KEY,
    run_id           BIGINT       NOT NULL REFERENCES benchmark_runs (id) ON DELETE CASCADE,
    suite_pr_id      BIGINT       NOT NULL REFERENCES suite_prs (id),
    bot_id           BIGINT       NOT NULL REFERENCES bots (id),
    mirror_pr_number INTEGER,
    mirror_pr_url    VARCHAR(500),
    base_branch      VARCHAR(255),
    head_branch      VARCHAR(255),
    status           VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    error_message    TEXT,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    completed_at     TIMESTAMPTZ
);

-- Comments collected from replay PRs (from bots) and from original PRs (from humans)
CREATE TABLE review_comments (
    id                  BIGSERIAL PRIMARY KEY,
    replay_pr_id        BIGINT       NOT NULL REFERENCES replay_prs (id) ON DELETE CASCADE,
    source              VARCHAR(50)  NOT NULL, -- BOT, ORIGINAL_HUMAN
    bot_id              BIGINT REFERENCES bots (id),
    github_comment_id   VARCHAR(100),
    comment_type        VARCHAR(50)  NOT NULL, -- REVIEW_COMMENT (inline), PR_COMMENT (general), REVIEW (review body)
    file_path           VARCHAR(1000),
    line_number         INTEGER,
    diff_hunk           TEXT,
    body                TEXT,
    body_normalized     TEXT,
    fingerprint_hash    VARCHAR(64),
    github_created_at   TIMESTAMPTZ,
    metadata_json       JSONB,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Human comments from the original exemplar PRs (ground truth)
CREATE TABLE original_comments (
    id                 BIGSERIAL PRIMARY KEY,
    suite_pr_id        BIGINT       NOT NULL REFERENCES suite_prs (id) ON DELETE CASCADE,
    github_comment_id  VARCHAR(100),
    author             VARCHAR(255),
    comment_type       VARCHAR(50)  NOT NULL,
    file_path          VARCHAR(1000),
    line_number        INTEGER,
    diff_hunk          TEXT,
    body               TEXT,
    body_normalized    TEXT,
    fingerprint_hash   VARCHAR(64),
    github_created_at  TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Human and machine gradings of comments
CREATE TABLE gradings (
    id                  BIGSERIAL PRIMARY KEY,
    comment_id          BIGINT      NOT NULL,
    comment_table_type  VARCHAR(50) NOT NULL, -- REVIEW, ORIGINAL
    grader_type         VARCHAR(50) NOT NULL, -- HUMAN, MACHINE
    grader_id           VARCHAR(255),         -- username or model name
    verdict             VARCHAR(50) NOT NULL, -- VALID, INVALID, DUPLICATE, NEEDS_REVIEW
    severity            VARCHAR(50),          -- CRITICAL, MAJOR, MINOR, NITPICK
    stars               INTEGER     NOT NULL DEFAULT 0 CHECK (stars BETWEEN 0 AND 5),
    flagged             BOOLEAN     NOT NULL DEFAULT FALSE,
    notes               TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Golden dataset: curated, approved issues used as ground truth for future runs
CREATE TABLE golden_dataset_entries (
    id                   BIGSERIAL PRIMARY KEY,
    suite_pr_id          BIGINT      NOT NULL REFERENCES suite_prs (id),
    source_comment_id    BIGINT,
    source_comment_type  VARCHAR(50),         -- REVIEW, ORIGINAL
    file_path            VARCHAR(1000),
    line_number          INTEGER,
    issue_type           VARCHAR(255),
    description          TEXT,
    canonical_body       TEXT,
    active               BOOLEAN     NOT NULL DEFAULT TRUE,
    included_by_default  BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Similarity scores between comments (used for deduplication and agreement tracking)
CREATE TABLE comment_similarities (
    id              BIGSERIAL PRIMARY KEY,
    comment_a_id    BIGINT      NOT NULL,
    comment_a_type  VARCHAR(50) NOT NULL, -- REVIEW, ORIGINAL
    comment_b_id    BIGINT      NOT NULL,
    comment_b_type  VARCHAR(50) NOT NULL,
    strategy        VARCHAR(100) NOT NULL,
    score           DOUBLE PRECISION,
    is_match        BOOLEAN,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_suite_prs_suite_id ON suite_prs (suite_id);
CREATE INDEX idx_replay_prs_run_id ON replay_prs (run_id);
CREATE INDEX idx_replay_prs_suite_pr_id ON replay_prs (suite_pr_id);
CREATE INDEX idx_replay_prs_status ON replay_prs (status);
CREATE INDEX idx_review_comments_replay_pr_id ON review_comments (replay_pr_id);
CREATE INDEX idx_review_comments_bot_id ON review_comments (bot_id);
CREATE INDEX idx_review_comments_source ON review_comments (source);
CREATE INDEX idx_original_comments_suite_pr_id ON original_comments (suite_pr_id);
CREATE INDEX idx_gradings_comment ON gradings (comment_id, comment_table_type);
CREATE INDEX idx_golden_dataset_suite_pr ON golden_dataset_entries (suite_pr_id);
CREATE INDEX idx_golden_dataset_active ON golden_dataset_entries (active);
CREATE INDEX idx_comment_similarities_a ON comment_similarities (comment_a_id, comment_a_type);
CREATE INDEX idx_comment_similarities_b ON comment_similarities (comment_b_id, comment_b_type);
CREATE INDEX idx_benchmark_runs_suite_id ON benchmark_runs (suite_id);
CREATE INDEX idx_benchmark_runs_status ON benchmark_runs (status);
