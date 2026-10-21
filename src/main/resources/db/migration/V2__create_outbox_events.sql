CREATE TABLE outbox_events (
   id UUID PRIMARY KEY,
   event_type VARCHAR(255) NOT NULL,
   cart_id VARCHAR(255) NOT NULL,
   payload TEXT NOT NULL,
   created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
   published BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_outbox_events_published
    ON outbox_events(published);