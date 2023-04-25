ALTER TABLE "process_versions" ALTER COLUMN "json" TYPE VARCHAR (5000000);
ALTER TABLE "process_versions" ALTER COLUMN "json" SET NOT NULL;
