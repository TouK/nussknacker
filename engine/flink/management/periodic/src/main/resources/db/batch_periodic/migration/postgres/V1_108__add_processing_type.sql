ALTER TABLE "periodic_processes" ADD COLUMN "processing_type" VARCHAR;

UPDATE "periodic_processes" SET "processing_type" = 'default';

ALTER TABLE "periodic_processes" ALTER COLUMN "processing_type" SET NOT NULL;
