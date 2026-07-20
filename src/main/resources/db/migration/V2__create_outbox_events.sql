CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    topic VARCHAR(200) NOT NULL,
    event_key VARCHAR(200) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error VARCHAR(1000),
    CONSTRAINT fk_outbox_events_video
        FOREIGN KEY (aggregate_id) REFERENCES video_processings (id) ON DELETE CASCADE,
    CONSTRAINT ck_outbox_events_attempts CHECK (attempts >= 0),
    CONSTRAINT ck_outbox_events_published
        CHECK (published_at IS NULL OR published_at >= occurred_at)
);

CREATE INDEX idx_outbox_events_pending
    ON outbox_events (next_attempt_at, occurred_at)
    WHERE published_at IS NULL;
