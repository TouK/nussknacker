ALTER TABLE "periodic_scenarios" RENAME TO "scheduled_scenarios";
ALTER TABLE "scheduled_scenarios" ALTER COLUMN "input_config_during_execution" DROP NOT NULL;
ALTER TABLE "scheduled_scenarios" ADD COLUMN "resolved_scenario_json" VARCHAR(10485760);
ALTER TABLE "scheduled_scenarios" ADD CONSTRAINT check_scheduled_scenarios
CHECK (
  ("active" = TRUE AND "input_config_during_execution" IS NOT NULL AND "resolved_scenario_json" IS NOT NULL)
    OR
  ("active" = FALSE)
);
ALTER TABLE "periodic_scenario_deployments" RENAME TO "scheduled_scenario_deployments";
