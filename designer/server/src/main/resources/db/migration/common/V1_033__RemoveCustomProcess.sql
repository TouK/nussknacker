DELETE FROM "processes" WHERE "type" = 'custom';
ALTER TABLE "processes" DROP COLUMN "type";

ALTER TABLE "process_versions" DROP COLUMN "main_class";
ALTER TABLE "process_versions" ALTER COLUMN "json" SET NOT NULL;
