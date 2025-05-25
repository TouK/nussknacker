CREATE TABLE flink_live_data
(
    scenario_id BIGINT PRIMARY KEY,
    live_data   LONGVARCHAR,
    updated_at  BIGINT NOT NULL
);
