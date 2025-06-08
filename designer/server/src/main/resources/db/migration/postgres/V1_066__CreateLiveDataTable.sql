CREATE TABLE live_data
(
    scenario_id  BIGINT NOT NULL,
    collector_id VARCHAR NOT NULL,
    live_data    TEXT,
    updated_at   BIGINT  NOT NULL,
    CONSTRAINT pk_scenario_id_collector_id PRIMARY KEY (scenario_id, collector_id)
);
