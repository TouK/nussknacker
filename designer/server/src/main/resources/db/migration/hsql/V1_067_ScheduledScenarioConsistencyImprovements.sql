-- 1. Fix missing or incorrect process_id using process_name
MERGE INTO scheduled_scenarios ss
    USING processes p
    ON ss.process_name = p.name
    WHEN MATCHED THEN
        UPDATE SET process_id = p.id;

-- 2. Copy orphaned scheduled_scenarios
CREATE TABLE scheduled_scenarios_orphaned_for_manual_review AS
SELECT *
FROM scheduled_scenarios
WHERE process_id IS NULL
   OR process_id NOT IN (SELECT id FROM processes);

-- 3. Remove orphaned scheduled_scenarios
DELETE FROM scheduled_scenarios
WHERE process_id IS NULL
   OR process_id NOT IN (SELECT id FROM processes);

-- 4. Add FK: scheduled_scenarios.process_id => processes.id
ALTER TABLE scheduled_scenarios
    ADD CONSTRAINT fk_scheduled_scenarios_process
        FOREIGN KEY (process_id)
            REFERENCES processes(id)
            ON DELETE CASCADE;

-- 5. Copy orphaned scheduled_scenario_deployments
CREATE TABLE scheduled_scenario_deployments_orphaned_for_manual_review AS
SELECT *
FROM scheduled_scenario_deployments
WHERE periodic_process_id NOT IN (
    SELECT id FROM scheduled_scenarios
);

-- 6. Remove orphaned deployments
DELETE FROM scheduled_scenario_deployments
WHERE periodic_process_id NOT IN (
    SELECT id FROM scheduled_scenarios
);

-- 7. Add FK: scheduled_scenario_deployments.periodic_process_id => scheduled_scenarios.id
ALTER TABLE scheduled_scenario_deployments
    ADD CONSTRAINT fk_scenario_deployments_process
        FOREIGN KEY (periodic_process_id)
            REFERENCES scheduled_scenarios(id)
            ON DELETE CASCADE;
