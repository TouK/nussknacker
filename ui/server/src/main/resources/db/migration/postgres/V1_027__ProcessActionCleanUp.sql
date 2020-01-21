-- DROP CONSTRAINTS
ALTER TABLE "process_deployment_info" DROP CONSTRAINT "pk_process_deployment_info";
ALTER TABLE "process_deployment_info" DROP CONSTRAINT "proc_ver_in_deployed_proc_fk";
ALTER TABLE "process_deployment_info" DROP CONSTRAINT "comment_id_in_deployed_proc_fk";

-- RENAME TABLE
ALTER TABLE "process_deployment_info" RENAME TO "process_actions";

-- FIELDS CHANGES
ALTER TABLE "process_actions" RENAME COLUMN "deployment_action" TO "action";
ALTER TABLE "process_actions" RENAME COLUMN "deploy_at" TO "created_at";
ALTER TABLE "process_actions" DROP COLUMN "environment";

-- ADD CONSTRAINTS
ALTER TABLE "process_actions" ADD CONSTRAINT "process_actions_pk" PRIMARY KEY ("process_id", "action", "created_at");
ALTER TABLE "process_actions" ADD CONSTRAINT "process_actions_version_fk" FOREIGN KEY ("process_id", "process_version_id") REFERENCES "process_versions" ("process_id", "id") ON DELETE CASCADE;
ALTER TABLE "process_actions" ADD CONSTRAINT "process_actions_comment_fk" FOREIGN KEY ("comment_id") REFERENCES "process_comments" ("id") ON DELETE CASCADE;
