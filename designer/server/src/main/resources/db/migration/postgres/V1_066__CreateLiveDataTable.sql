CREATE TABLE "live_data"
(
    "scenario_id"            BIGINT  NOT NULL,
    "deployment_id"          VARCHAR NOT NULL,
    "external_deployment_id" VARCHAR NOT NULL,
    "collector_id"           VARCHAR NOT NULL,
    "live_data"              TEXT,
    "updated_at"             BIGINT  NOT NULL
);

ALTER TABLE "live_data"
    ADD CONSTRAINT pk_scenario_activity_collector_ids PRIMARY KEY ("scenario_id", "deployment_id", "external_deployment_id", "collector_id")
