INSERT INTO scenario_activities (activity_type,
                                 scenario_id,
                                 activity_id,
                                 user_id,
                                 user_name,
                                 impersonated_by_user_id,
                                 impersonated_by_user_name,
                                 last_modified_by_user_name,
                                 last_modified_at,
                                 created_at,
                                 scenario_version,
                                 comment,
                                 attachment_id,
                                 performed_at,
                                 state,
                                 error_message,
                                 additional_properties)
SELECT 'PERFORMED_SCHEDULED_EXECUTION',
       ss.process_id,
       generate_random_uuid(),
       NULL,
       'Nussknacker',
       NULL,
       NULL,
       'Nussknacker',
       ssd.created_at,
       COALESCE(ssd.deployed_at, ssd.completed_at),
       ss.process_version_id,
       NULL,
       NULL,
       ssd.completed_at,
       'FINISHED',
       NULL,
       jsonb_strip_nulls(jsonb_build_object(
               'imported_deployment_id', ssd.id::text,
               'scheduleName', COALESCE(ssd.schedule_name, '[default]'),
               'status', CASE ssd.status
                             WHEN 'Finished' THEN 'FINISHED'
                             WHEN 'Failed' THEN 'FAILED'
                             WHEN 'RetryingDeploy' THEN 'DEPLOYMENT_WILL_BE_RETRIED'
                             WHEN 'FailedOnDeploy' THEN 'DEPLOYMENT_FAILED'
                   END,
               'createdAt',
               to_char(ssd.created_at AT TIME ZONE (SELECT zone_id FROM jvm_timezone), 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'),
               'nextRetryAt', CASE
                                  WHEN ssd.next_retry_at IS NULL THEN NULL
                                  ELSE to_char(ssd.next_retry_at AT TIME ZONE (SELECT zone_id FROM jvm_timezone),
                                               'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') END,
               'retriesLeft', CASE WHEN ssd.next_retry_at IS NULL THEN NULL ELSE ssd.retries_left::text END
                         )) ::text
FROM scheduled_scenario_deployments ssd
         JOIN scheduled_scenarios ss ON ss.id = ssd.periodic_process_id
WHERE ssd.status IN ('Finished', 'Failed', 'RetryingDeploy', 'FailedOnDeploy');

-- Drop the temporary jvm_timezone table, that is created by 068 migration, and then used in this migration
DROP TABLE jvm_timezone;
