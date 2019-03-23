ALTER TABLE "processes" ADD COLUMN "processing_type" VARCHAR(254);

UPDATE "processes" SET "processing_type" = 'streaming';

ALTER TABLE "processes" ALTER COLUMN "processing_type" SET NOT NULL;