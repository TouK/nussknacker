UPDATE "process_deployment_info" AS pdi SET "process_version_id" = (
    SELECT pv."id" from "process_versions" as pv
    WHERE
            pv."process_id" = pdi."process_id" and
            pv."create_date" < pdi."deploy_at"
    ORDER BY pv."create_date" DESC
    LIMIT 1
)
WHERE pdi."process_version_id" IS NULL;

ALTER TABLE "process_deployment_info" ALTER COLUMN "process_version_id" set not null;