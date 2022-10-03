UPDATE "process_deployment_info" pdi SET pdi."process_version_id" = (
    SELECT pv."id" from "process_versions" AS pv
    WHERE
            pv."process_id" = pdi."process_id" AND
            pv."create_date" < pdi."deploy_at"
    ORDER BY pv."create_date" DESC
    LIMIT 1
)
WHERE pdi."process_version_id" IS NULL;

ALTER TABLE "process_deployment_info" ALTER COLUMN "process_version_id" set not null;