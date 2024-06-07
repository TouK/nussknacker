ALTER TABLE "processes"
    ADD COLUMN "latest_version_id" INTEGER;
ALTER TABLE "processes"
    ADD COLUMN "latest_finished_action_id" UUID;
ALTER TABLE "processes"
    ADD COLUMN "latest_finished_cancel_action_id" UUID;
ALTER TABLE "processes"
    ADD COLUMN "latest_finished_deploy_action_id" UUID;

UPDATE "processes" p
SET "latest_version_id"                = (SELECT pv."id"
                                          FROM "process_versions" pv
                                          WHERE pv."process_id" = p."id"
                                          ORDER BY pv."create_date" DESC
                                          LIMIT 1),
    "latest_finished_action_id"        = (SELECT pa."id"
                                          FROM "process_actions" pa
                                          WHERE pa."process_id" = p."id"
                                            AND (pa."state" = 'FINISHED' or pa."state" = 'EXECUTION_FINISHED')
                                          ORDER BY pa."created_at" DESC
                                          LIMIT 1),
    "latest_finished_cancel_action_id" = (SELECT pa."id"
                                          FROM "process_actions" pa
                                          WHERE pa."process_id" = p."id"
                                            AND (pa."state" = 'FINISHED' or pa."state" = 'EXECUTION_FINISHED')
                                            AND pa."action_name" = 'CANCEL'
                                          ORDER BY pa."created_at" DESC
                                          LIMIT 1),
    "latest_finished_deploy_action_id" = (SELECT pa."id"
                                          FROM "process_actions" pa
                                          WHERE pa."process_id" = p."id"
                                            AND (pa."state" = 'FINISHED' or pa."state" = 'EXECUTION_FINISHED')
                                            AND pa."action_name" = 'DEPLOY'
                                          ORDER BY pa."created_at" DESC
                                          LIMIT 1);

ALTER TABLE "processes"
    ADD CONSTRAINT "fk_process_latest_version" FOREIGN KEY ("id", "latest_version_id") REFERENCES "process_versions" ("process_id", "id") ON DELETE CASCADE;

ALTER TABLE "processes"
    ADD CONSTRAINT "fk_process_latest_finished_action" FOREIGN KEY ("latest_finished_action_id") REFERENCES "process_actions" ("id") ON DELETE CASCADE;

ALTER TABLE "processes"
    ADD CONSTRAINT "fk_process_latest_finished_cancel_action" FOREIGN KEY ("latest_finished_cancel_action_id") REFERENCES "process_actions" ("id") ON DELETE CASCADE;

ALTER TABLE "processes"
    ADD CONSTRAINT "fk_process_latest_finished_deploy_action" FOREIGN KEY ("latest_finished_deploy_action_id") REFERENCES "process_actions" ("id") ON DELETE CASCADE;



WITH latest_versions AS (SELECT pv."process_id",
                                pv."id" AS "latest_version_id"
                         FROM "process_versions" pv
                                  JOIN (SELECT "process_id",
                                               MAX("create_date") AS "max_create_date"
                                        FROM "process_versions"
                                        GROUP BY "process_id") sub_pv ON pv."process_id" = sub_pv."process_id" AND
                                                                         pv."create_date" = sub_pv."max_create_date"),
     latest_actions AS (SELECT pa."process_id",
                               MAX(CASE
                                       WHEN (pa."state" = 'FINISHED' OR pa."state" = 'EXECUTION_FINISHED')
                                           THEN pa."id" END)                              AS "latest_finished_action_id",
                               MAX(CASE
                                       WHEN (pa."state" = 'FINISHED' OR pa."state" = 'EXECUTION_FINISHED') AND
                                            pa."action_name" = 'CANCEL'
                                           THEN pa."id" END)                              AS "latest_finished_cancel_action_id",
                               MAX(CASE
                                       WHEN (pa."state" = 'FINISHED' OR pa."state" = 'EXECUTION_FINISHED') AND
                                            pa."action_name" = 'DEPLOY'
                                           THEN pa."id" END)                              AS "latest_finished_deploy_action_id"
                        FROM "process_actions" pa
                        GROUP BY pa."process_id")
UPDATE "processes" p
SET "latest_version_id"                = lv."latest_version_id",
    "latest_finished_action_id"        = la."latest_finished_action_id",
    "latest_finished_cancel_action_id" = la."latest_finished_cancel_action_id",
    "latest_finished_deploy_action_id" = la."latest_finished_deploy_action_id"
FROM latest_versions lv
         JOIN latest_actions la ON p."id" = la."process_id"
WHERE p."id" = lv."process_id";
