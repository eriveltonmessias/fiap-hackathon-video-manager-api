CREATE TABLE notification_failures (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    video_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    channel VARCHAR(20),
    reason VARCHAR(500) NOT NULL,
    failed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_notification_failures_video
        FOREIGN KEY (video_id) REFERENCES video_processings (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_failures_event
        FOREIGN KEY (event_id) REFERENCES processed_video_events (event_id) ON DELETE CASCADE,
    CONSTRAINT ck_notification_failures_channel
        CHECK (channel IS NULL OR channel IN ('EMAIL', 'TELEGRAM'))
);

CREATE INDEX idx_notification_failures_video_id
    ON notification_failures (video_id);
