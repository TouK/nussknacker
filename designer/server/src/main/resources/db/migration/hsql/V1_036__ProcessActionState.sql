--- DROP CONSTRAINTS
ALTER TABLE "process_actions" DROP PRIMARY KEY;
ALTER TABLE "process_actions" ALTER COLUMN "performed_at" SET NULL;

--- ADD NEW COLUMNS AND SEQUENCE
ALTER TABLE "process_actions" ADD COLUMN "id" INTEGER;
ALTER TABLE "process_actions" ADD COLUMN "state" VARCHAR(254);
ALTER TABLE "process_actions" ADD COLUMN "created_at" TIMESTAMP;
CREATE SEQUENCE "process_actions_id_sequence";

--- UPDATE OLD VALUES
UPDATE "process_actions" set "id" = "process_actions_id_sequence".nextval, "state" = 'FINISHED', "created_at" = "perfromed_at";

--- ADD CONSTRAINTS
ALTER TABLE "process_actions" ADD CONSTRAINT "process_actions_pk" PRIMARY KEY ("id");
ALTER TABLE "process_actions" ALTER COLUMN "state" SET NOT NULL;
ALTER TABLE "process_actions" ALTER COLUMN "created_at" SET NOT NULL;

--- REVERT CONSTRAINT AS AN INDEX
CREATE INDEX "performed_at_idx" ON "process_actions" ("process_id", "action", "performed_at");