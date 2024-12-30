# Periodic scenarios deployment manager

An experimental engine running scenarios periodicly according to a schedule such as a cron expression.

When the deploy button is clicked in NK GUI, then the scenario is scheduled to be run in the future. When a scenario
should be run is described by a schedule, e.g. a cron expression set in scenario properties. During scenario scheduling,
deployment manager only prepares data needed to deploy scenario on a target engine (e.g. on Flink cluster).
scenario is deployed according to the schedule on the target engine. Periodic engine watches its completion. Afterwards
scenario is scheduled to be run again according to the schedule.

## Usage

- Implement `DeploymentManagerProvider` using `PeriodicDeploymentManagerProvider`. Following components need to provided:
  - Underlying engine, currently only Flink is supported.
  - Optional `SchedulePropertyExtractorFactory` to determine how to construct an instance of a periodic property. By default
    a cron expression set in scenario properties is used to describe when a scenario should be run.
  - Optional `ProcessConfigEnricherFactory` if you would like to extend scenario configuration, by default nothing is done.
  - Optional `PeriodicProcessListenerFactory` to take some actions on scenario lifecycle.
  - Optional `AdditionalDeploymentDataProvider` to inject additional deployment parameters.
- Add service provider with your `DeploymentManagerProvider` implementation.

## Configuration

Use `deploymentManager` with the following properties:

- `db` - optional configuration of the legacy, custom Periodic DM database; deprecated and will be removed in the future; when config not present, then the default Nu database will be used
- `processingType` - processing type of scenarios to be managed by this instance of the periodic engine.
- `rescheduleCheckInterval` - frequency of checking finished scenarios to be rescheduled. Optional.
- `deployInterval` - frequency of checking scenarios to be deployed on Flink cluster. Optional.
- `deploymentRetry` - failed deployments configuration. By default retrying is disabled.
  - `deployMaxRetries` - maximum amount of retries for failed deployment.
  - `deployRetryPenalize` - an amount of time by which the next retry should be delayed.
- `jarsDir` -  directory for jars storage.
- `maxFetchedPeriodicScenarioActivities` - optional, maximum number of latest ScenarioActivities that will be fetched, by default 200
