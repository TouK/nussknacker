ALTER TABLE "process_deployment_info" ADD COLUMN "comment_id" INTEGER;

ALTER TABLE "process_deployment_info"
ADD CONSTRAINT "comment_id_in_deployed_proc_fk" FOREIGN KEY ("comment_id") REFERENCES "process_comments" ("id") ON DELETE CASCADE;