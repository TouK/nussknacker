ALTER TABLE "periodic_process_deployments" ADD COLUMN "schedule_name" VARCHAR(512);
ALTER TABLE "periodic_processes" RENAME "periodic_property" TO "schedule_property";