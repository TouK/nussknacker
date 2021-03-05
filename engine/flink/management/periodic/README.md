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
  - Optional `PeriodicPropertyExtractor` to determine how to construct an instance of a periodic property. By default
    a cron expression set in process properties is used to describe when a process should be run.
  - Optional `EnrichDeploymentWithJarDataFactory` if you would like to, for example, extend process configuration,
    by default nothing is done.
- Add service provider with your `ProcessManagerProvider` implementation.
                                                                      
## Changes (TODO: document properly)
- Process can have complex schedule - e.g. map of CronExpressions. The idea is that we can run process 
  with different schedules, but we also want to know what is schedule of current run. For example:
  - daily on 03:00 we run job to process event from day *n-1*
  - daily on 01:00 we run job to process event from day *n-2*  
- PeriodicProcessListener is introduced, so that implementations can react to scheduling, deployment and finishing of 
  periodic jobs
- Details about scheduled run are passed to job via DeploymentData. This includes scheduled date (which may 
  be different from real date), schedule name and deployment ids. 

Future changes:
- Add possibility to retry failed invocations
- Tables normalisation - e.g. different table for schedules