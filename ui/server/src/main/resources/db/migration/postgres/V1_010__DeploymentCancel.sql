ALTER TABLE "deployed_process_versions" RENAME TO "process_deployment_info";

ALTER TABLE "process_deployment_info" ADD "deployment_action" VARCHAR(256);

UPDATE "process_deployment_info" set "deployment_action" = 'DEPLOY';

ALTER TABLE "process_deployment_info" ALTER COLUMN "deployment_action" set not null;

ALTER TABLE "process_deployment_info" DROP CONSTRAINT "pk_deployed_process_version";

ALTER TABLE "process_deployment_info" ALTER COLUMN "process_version_id" drop not null;

ALTER TABLE "process_deployment_info" ADD CONSTRAINT "pk_process_deployment_info" PRIMARY KEY ("process_id", "deployment_action", "environment", "deploy_at");

ALTER TABLE "process_deployment_info"
ADD CONSTRAINT "process_deployment_info_check" CHECK ("deployment_action" in ('DEPLOY', 'CANCEL'));

