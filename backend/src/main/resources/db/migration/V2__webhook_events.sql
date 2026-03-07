CREATE TABLE webhook_events (
    id              BIGSERIAL PRIMARY KEY,
    delivery_id     VARCHAR(100) UNIQUE,  -- X-GitHub-Delivery header
    event_type      VARCHAR(100) NOT NULL, -- X-GitHub-Event header
    action          VARCHAR(100),          -- payload.action
    repo_full_name  VARCHAR(255),          -- payload.repository.full_name
    pr_number       INTEGER,               -- payload.pull_request.number or payload.check_run.pull_requests[0].number
    payload         TEXT,                  -- raw JSON payload
    processed_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_webhook_events_repo_pr ON webhook_events(repo_full_name, pr_number);
CREATE INDEX idx_webhook_events_delivery ON webhook_events(delivery_id);
