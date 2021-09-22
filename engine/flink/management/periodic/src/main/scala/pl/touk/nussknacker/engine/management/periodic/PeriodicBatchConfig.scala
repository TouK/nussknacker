package pl.touk.nussknacker.engine.management.periodic

import com.typesafe.config.Config

import scala.concurrent.duration.{Duration, FiniteDuration}

/**
  * Periodic Flink scenarios deployment configuration.
  *
  * @param db Nussknacker db configuration.
  * @param rescheduleCheckInterval {@link RescheduleFinishedActor} check interval.
  * @param deployInterval {@link DeploymentActor} check interval.
  * @param deploymentRetry {@link DeploymentRetryConfig}  for deployment failure recovery.
  * @param jarsDir Directory for jars storage.
  */
case class PeriodicBatchConfig(db: Config,
                               rescheduleCheckInterval: FiniteDuration,
                               deployInterval: FiniteDuration,
                               deploymentRetry: DeploymentRetryConfig = DeploymentRetryConfig(),
                               jarsDir: String)

/**
  * Periodic Flink scenarios deployment retry configuration. Used by {@link PeriodicBatchConfig}
  * This config is only for retries of failures during scenario deployment. Failure recovery of running scenario should be handled by Flink's restart strategy.
  *
  * @param deployMaxRetries Maximum amount of retries for failed deployment. Default is zero.
  * @param deployRetryPenalize An amount of time by which the next retry should be delayed. Default is zero.
  */
case class DeploymentRetryConfig(deployMaxRetries: Int = 0, deployRetryPenalize: FiniteDuration = Duration.Zero)
