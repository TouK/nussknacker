ALTER TABLE "processes"
    ADD COLUMN "latest_version_id" INTEGER;
ALTER TABLE "processes"
    ADD COLUMN "latest_finished_action_id" INTEGER;

UPDATE "processes" p
SET "latest_version_id"         = (SELECT pv."id"
                                   FROM "process_versions" pv
                                   WHERE pv."process_id" = p."id"
                                   ORDER BY pv."create_date" DESC
                                   LIMIT 1),
    "latest_finished_action_id" = (SELECT pa."id"
                                   FROM "process_actions" pa
                                   WHERE pa."process_id" = p."id"
                                     AND (pa."state" = 'FINISHED' OR pa."state" = 'EXECUTION_FINISHED')
                                   ORDER BY pa."created_at" DESC
                                   LIMIT 1);

ALTER TABLE "processes"
    ADD CONSTRAINT "fk_process_latest_version" FOREIGN KEY ("id", "latest_version_id") REFERENCES "process_versions" ("process_id", "id") ON DELETE CASCADE;

ALTER TABLE "processes"
    ADD CONSTRAINT "fk_process_latest_finished_action" FOREIGN KEY ("id", "latest_finished_action_id") REFERENCES "process_actions" ("process_id", "id") ON DELETE CASCADE;

ALTER TABLE "processes"
    ALTER COLUMN "latest_version_id" SET NOT NULL;

