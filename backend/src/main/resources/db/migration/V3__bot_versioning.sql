-- Snapshot of bot configuration at the time a run was started
CREATE TABLE bot_snapshots (
    id                  BIGSERIAL PRIMARY KEY,
    run_id              BIGINT      NOT NULL REFERENCES benchmark_runs(id) ON DELETE CASCADE,
    bot_id              BIGINT      NOT NULL REFERENCES bots(id),
    name                VARCHAR(255) NOT NULL,
    workflow_file_name  VARCHAR(255),
    workflow_content    TEXT,
    wait_strategy       VARCHAR(50)  NOT NULL,
    timeout_seconds     INTEGER      NOT NULL,
    snapshotted_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (run_id, bot_id)
);
CREATE INDEX idx_bot_snapshots_run_id ON bot_snapshots(run_id);
