CREATE TABLE scheduled_runs (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    suite_id        BIGINT NOT NULL REFERENCES benchmark_suites(id),
    bot_ids         TEXT NOT NULL,   -- JSON array of bot IDs: [1,2,3]
    cron_expression VARCHAR(100) NOT NULL,  -- standard cron: "0 0 * * MON" = every Monday midnight
    concurrency     INTEGER NOT NULL DEFAULT 2,
    golden_dataset_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    enabled         BOOLEAN NOT NULL DEFAULT TRUE,
    last_run_at     TIMESTAMPTZ,
    last_run_id     BIGINT REFERENCES benchmark_runs(id),
    last_run_status VARCHAR(50),
    next_run_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_scheduled_runs_suite_id ON scheduled_runs(suite_id);
CREATE INDEX idx_scheduled_runs_enabled ON scheduled_runs(enabled);
