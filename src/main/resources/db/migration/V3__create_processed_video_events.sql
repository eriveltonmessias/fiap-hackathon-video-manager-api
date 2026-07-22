CREATE TABLE processed_video_events (
    event_id UUID PRIMARY KEY,
    video_id UUID NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_processed_video_events_video
        FOREIGN KEY (video_id) REFERENCES video_processings (id) ON DELETE CASCADE,
    CONSTRAINT ck_processed_video_events_type
        CHECK (event_type IN ('VideoProcessed', 'VideoProcessingFailed'))
);

CREATE INDEX idx_processed_video_events_video_id
    ON processed_video_events (video_id);
