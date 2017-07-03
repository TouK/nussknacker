
ALTER TABLE "tags" DROP CONSTRAINT "tag-process-fk";
ALTER TABLE "tags" ADD CONSTRAINT "tag-process-fk" FOREIGN KEY ("process_id") REFERENCES "processes" ("id") ON DELETE CASCADE;

ALTER TABLE "process_versions" ADD CONSTRAINT "version-process-fk" FOREIGN KEY ("process_id") REFERENCES "processes" ("id") ON DELETE CASCADE;

ALTER TABLE "process_comments" DROP CONSTRAINT "proc_comments_proc_version_fk";
ALTER TABLE "process_comments"
ADD CONSTRAINT "proc_comments_proc_version_fk" FOREIGN KEY ("process_id", "process_version_id") REFERENCES "process_versions" ("process_id", "id") ON DELETE CASCADE;


ALTER TABLE "process_deployment_info"
DROP CONSTRAINT "proc_ver_in_deployed_proc_fk";
ALTER TABLE "process_deployment_info"
ADD CONSTRAINT "proc_ver_in_deployed_proc_fk" FOREIGN KEY ("process_id", "process_version_id") REFERENCES "process_versions" ("process_id", "id") ON DELETE CASCADE;
