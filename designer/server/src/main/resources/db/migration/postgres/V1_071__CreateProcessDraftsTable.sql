CREATE TABLE "process_drafts"
(
    "process_id"        BIGINT    NOT NULL,
    "scenario_graph"    TEXT      NOT NULL,
    "base_version_id"   BIGINT,
    "updated_at"        TIMESTAMP NOT NULL,
    "updated_by"        VARCHAR   NOT NULL
);

ALTER TABLE "process_drafts"
    ADD CONSTRAINT process_drafts_pk PRIMARY KEY ("process_id");

ALTER TABLE "process_drafts"
    ADD CONSTRAINT process_drafts_process_fk
        FOREIGN KEY ("process_id") REFERENCES "processes" ("id") ON UPDATE CASCADE ON DELETE CASCADE;
