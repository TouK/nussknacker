ALTER TABLE "periodic_processes" ADD COLUMN "model_config" VARCHAR(16777216);
ALTER TABLE "periodic_processes" ADD COLUMN "build_info_json" VARCHAR(1048576);
ALTER TABLE "periodic_processes" ADD COLUMN "jar_file_name" VARCHAR(1024);

UPDATE "periodic_processes" SET "model_config" = '', "build_info_json" = '', "jar_file_name" = '';

ALTER TABLE "periodic_processes" ALTER COLUMN "model_config" SET NOT NULL;
ALTER TABLE "periodic_processes" ALTER COLUMN "build_info_json" SET NOT NULL;
ALTER TABLE "periodic_processes" ALTER COLUMN "jar_file_name" SET NOT NULL;
