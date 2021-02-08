ALTER TABLE "process_actions" DROP CONSTRAINT "process_deployment_info_check";

ALTER TABLE "process_actions"
    ADD CONSTRAINT "process_actions_check" CHECK ("action" in ('DEPLOY', 'CANCEL', 'ARCHIVE', 'UNARCHIVE'));

INSERT INTO "process_actions" ("process_version_id", "user", "performed_at", "build_info", "action", "process_id", "comment_id")
    (SELECT
         (
             SELECT pv."id" from "process_versions" pv
             WHERE pv."process_id" = p."id"
             ORDER BY pv."create_date" DESC
             LIMIT 1
    ) as process_version,
         'Nussknacker' as user,
         NOW() as performed_at,
         Null as build_info,
         'ARCHIVE' as action,
         p."id" as process_id,
         Null as comment_id
FROM "processes" p
WHERE p."is_archived" = true);