ALTER TABLE "process_actions" RENAME COLUMN "action_type" TO "action_name";

ALTER TABLE "process_actions" DROP CONSTRAINT "process_actions_check";
