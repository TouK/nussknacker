CREATE TABLE "live_data"
(
    "scenario_id"            BIGINT  NOT NULL,
    "deployment_id"          UUID NOT NULL,
    "collector_id"           VARCHAR NOT NULL,
    "live_data"              LONGVARCHAR,
    "updated_at"             BIGINT  NOT NULL
);

ALTER TABLE "live_data"
    ADD CONSTRAINT pk_live_data PRIMARY KEY ("scenario_id", "deployment_id", "collector_id")
