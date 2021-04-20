ALTER TABLE "periodic_process_deployments" ADD COLUMN "schedule_name" VARCHAR(512);
ALTER TABLE "periodic_processes" ALTER COLUMN "periodic_property" RENAME TO "schedule_property";