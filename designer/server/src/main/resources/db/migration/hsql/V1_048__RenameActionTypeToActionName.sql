ALTER TABLE "process_actions" ALTER COLUMN "action_type" RENAME TO "action_name";

ALTER TABLE "process_actions" DROP CONSTRAINT "process_actions_check";
