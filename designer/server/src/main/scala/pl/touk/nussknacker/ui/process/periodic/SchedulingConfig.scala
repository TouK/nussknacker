package pl.touk.nussknacker.ui.process.periodic

import com.typesafe.config.Config

import scala.concurrent.duration._

/**
  * Periodic Flink scenarios deployment configuration.
  *
  * @param legacyDb Optional custom db, that will be used instead of main Nussknacker DB. Will be removed in the future.
  * @param processingType processing type of scenarios to be managed by this instance of the periodic engine.
  * @param rescheduleCheckInterval {@link RescheduleFinishedActor} check interval.
  * @param deployInterval {@link DeploymentActor} check interval.
  * @param deploymentRetry {@link DeploymentRetryConfig}  for deployment failure recovery.
  * @param maxFetchedPeriodicScenarioActivities Optional limit of number of latest periodic-related Scenario Activities that are returned by Periodic DM.
  */
case class SchedulingConfig(
    legacyDb: Option[Config],
    // The `processingType` value should be removed in the future, because it should correspond to the real value of processingType.
    // But at the moment it may not be equal to the value of processingType of the DM that uses scheduling mechanism.
    // Therefore, we must keep the separate value SchedulingConfig, until we ensure consistency between the real processingType and the one defined here.
    processingType: String,
    rescheduleCheckInterval: FiniteDuration = 13 seconds,
    deployInterval: FiniteDuration = 17 seconds,
    deploymentRetry: DeploymentRetryConfig,
    executionConfig: PeriodicExecutionConfig,
    maxFetchedPeriodicScenarioActivities: Option[Int] = Some(200),
)

/**
  * Periodic Flink scenarios deployment retry configuration. Used by {@link PeriodicBatchConfig}
  * This config is only for retries of failures during scenario deployment. Failure recovery of running scenario should be handled by Flink's restart strategy.
  *
  * @param deployMaxRetries Maximum amount of retries for failed deployment. Default is zero.
  * @param deployRetryPenalize An amount of time by which the next retry should be delayed. Default is zero.
  */
case class DeploymentRetryConfig(deployMaxRetries: Int = 0, deployRetryPenalize: FiniteDuration = Duration.Zero)

case class PeriodicExecutionConfig(rescheduleOnFailure: Boolean = false)
