INSERT INTO scenario_activities (
    activity_type,
    scenario_id,
    activity_id,
    user_id,
    user_name,
    created_at,
    performed_at,
    scenario_version,
    state,
    additional_properties
)
SELECT
    'PerformedScheduledExecution',
    s.process_id,
    "#your_schema_name".generate_random_uuid(),
    NULL,
    'System',
    d.created_at,
    d.completed_at,
    s.process_version_id,
    d.status,
    '{}'
FROM scheduled_scenario_deployments d
         JOIN scheduled_scenarios s ON d.periodic_process_id = s.id
WHERE d.completed_at IS NOT NULL;
