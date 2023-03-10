--- ENABLES uuid-ossp to make uuid_generate_v4() function available
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

--- DROP CONSTRAINTS
ALTER TABLE "process_actions" DROP CONSTRAINT "process_actions_pk";
ALTER TABLE "process_actions" ALTER COLUMN "performed_at" DROP NOT NULL;
--- for in-progress actions, version will be null
ALTER TABLE "process_actions" ALTER COLUMN "process_version_id" DROP NOT NULL;

--- ADD NEW COLUMNS AND SEQUENCE
ALTER TABLE "process_actions" ADD COLUMN "id" UUID;
ALTER TABLE "process_actions" ADD COLUMN "state" VARCHAR(16);
ALTER TABLE "process_actions" ADD COLUMN "created_at" TIMESTAMP;
ALTER TABLE "process_actions" ADD COLUMN "failure_message" VARCHAR(1022);

--- UPDATE OLD VALUES
UPDATE "process_actions" set "id" = uuid_generate_v4(), "state" = 'FINISHED', "created_at" = "performed_at";

--- ADD CONSTRAINTS
ALTER TABLE "process_actions" ADD CONSTRAINT "process_actions_pk" PRIMARY KEY ("id");
ALTER TABLE "process_actions" ALTER COLUMN "state" SET NOT NULL;
ALTER TABLE "process_actions" ALTER COLUMN "created_at" SET NOT NULL;

ALTER TABLE "process_actions" ADD CONSTRAINT "process_actions_state_values" CHECK ("state" in ('IN_PROGRESS', 'FINISHED', 'FAILED'));
ALTER TABLE "process_actions" ADD CONSTRAINT "process_actions_version_id_not_null" CHECK ("action" <> 'DEPLOY' OR "process_version_id" IS NOT NULL);