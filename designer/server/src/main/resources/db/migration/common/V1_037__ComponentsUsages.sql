ALTER TABLE "process_versions" ADD COLUMN "components_usages" VARCHAR(1000000);

-- Set empty JSON array.
UPDATE "process_versions" SET "components_usages" = '[]';

ALTER TABLE "process_versions" ALTER COLUMN "components_usages" SET NOT NULL;
