CREATE TABLE video_processings (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL,
    input_object_key VARCHAR(1024),
    output_object_key VARCHAR(1024),
    failure_reason VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_video_processings_status
        CHECK (status IN ('RECEIVED', 'STORED', 'PENDING_PROCESSING', 'PROCESSING', 'PROCESSED', 'FAILED')),
    CONSTRAINT ck_video_processings_updated_at
        CHECK (updated_at >= created_at),
    CONSTRAINT ck_video_processings_state
        CHECK (
            (status = 'RECEIVED' AND input_object_key IS NULL AND output_object_key IS NULL AND failure_reason IS NULL)
            OR (status IN ('STORED', 'PENDING_PROCESSING', 'PROCESSING') AND input_object_key IS NOT NULL AND output_object_key IS NULL AND failure_reason IS NULL)
            OR (status = 'PROCESSED' AND input_object_key IS NOT NULL AND output_object_key IS NOT NULL AND failure_reason IS NULL)
            OR (status = 'FAILED' AND output_object_key IS NULL AND failure_reason IS NOT NULL)
        )
);

CREATE INDEX idx_video_processings_customer_created_at
    ON video_processings (customer_id, created_at DESC);
