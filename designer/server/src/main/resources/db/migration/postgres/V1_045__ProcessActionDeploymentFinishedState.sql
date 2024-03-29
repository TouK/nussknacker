ALTER TABLE "process_actions" DROP CONSTRAINT "process_actions_state_values";

ALTER TABLE "process_actions" ALTER COLUMN "state" TYPE VARCHAR(32);
ALTER TABLE "process_actions" ADD CONSTRAINT "process_actions_state_values" CHECK ("state" in ('IN_PROGRESS', 'FINISHED', 'FAILED', 'EXECUTION_FINISHED'));