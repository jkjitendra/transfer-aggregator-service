ALTER TABLE audit_events ADD COLUMN trace_id CHAR(32);
ALTER TABLE audit_events ADD COLUMN request_id VARCHAR(64);
CREATE INDEX audit_trace_idx ON audit_events (trace_id) WHERE trace_id IS NOT NULL;
