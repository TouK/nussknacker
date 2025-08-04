-- 1. Fix missing or incorrect process_id using process_name
MERGE INTO "scheduled_scenarios" AS ss
    USING "processes" AS p
    ON ss."process_name" = p."name"
    WHEN MATCHED THEN
        UPDATE SET ss."process_id" = p."id";

-- 2. The HSQLDB version of the migration does not perform backup of the orphaned data, see Postgres version

-- 3. Remove orphaned scheduled_scenarios
DELETE FROM "scheduled_scenarios"
WHERE "process_id" IS NULL
   OR NOT EXISTS (
    SELECT 1 FROM "processes" p WHERE p."id" = "scheduled_scenarios"."process_id"
);

-- 4. Add FK: scheduled_scenarios.process_id => processes.id
ALTER TABLE "scheduled_scenarios"
    ADD CONSTRAINT "fk_scheduled_scenarios_process"
        FOREIGN KEY ("process_id")
            REFERENCES "processes"("id")
            ON DELETE CASCADE;

-- 5. The HSQLDB version of the migration does not perform backup of the orphaned data, see Postgres version

/* 6. Remove orphaned deployments */
DELETE FROM "scheduled_scenario_deployments"
WHERE NOT EXISTS (
    SELECT 1 FROM "scheduled_scenarios" s WHERE s."id" = "scheduled_scenario_deployments"."periodic_process_id"
);

/* 7. Add FK: scheduled_scenario_deployments.periodic_process_id => scheduled_scenarios.id */
ALTER TABLE "scheduled_scenario_deployments"
    ADD CONSTRAINT "fk_scenario_deployments_process"
        FOREIGN KEY ("periodic_process_id")
            REFERENCES "scheduled_scenarios"("id")
            ON DELETE CASCADE;
