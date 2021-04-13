# Periodic processes engine

An experimental engine running processes periodicly according to a schedule such as a cron expression.

When the deploy button is clicked in NK GUI, then the process is scheduled to be run in the future. When a process
should be run is described by a schedule, e.g. a cron expression set in process properties. During process scheduling,
periodic process manager only prepares data needed to deploy process on a target engine (e.g. on Flink cluster).
Process is deployed according to the schedule on the target engine. Periodic engine watches its completion. Afterwards
process is scheduled to be run again according to the schedule.

## Usage

- Implement `ProcessManagerProvider` using `PeriodicProcessManagerProvider`. Following components need to provided:
  - Underlying engine, currently only Flink is supported.
  - Optional `SchedulePropertyExtractor` to determine how to construct an instance of a periodic property. By default
    a cron expression set in process properties is used to describe when a process should be run.
  - Optional `EnrichDeploymentWithJarDataFactory` if you would like to, for example, extend process configuration,
    by default nothing is done.
- Add service provider with your `ProcessManagerProvider` implementation.
