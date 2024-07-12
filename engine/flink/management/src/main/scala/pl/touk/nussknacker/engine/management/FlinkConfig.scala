package pl.touk.nussknacker.engine.management

import pl.touk.nussknacker.engine.api.deployment.cache.ScenarioStateCachingConfig

import scala.concurrent.duration.{DurationInt, FiniteDuration}

/**
  * FlinkConfig deployment configuration.
  *
  * @param restUrl REST API endpoint of the Flink cluster.
  * @param jobManagerTimeout Timeout for communication with FLink cluster. Consider extending if e.g. you have long savepoint times etc.
  * @param shouldVerifyBeforeDeploy By default, before redeployment of scenario with state from savepoint, verification of savepoint compatibility is performed. There are some cases when it can be too time consuming or not possible. Use this flag to disable it.
  * @param shouldCheckAvailableSlots When true {@link FlinkDeploymentManager} checks if there are free slots to run new job. This check should be disabled on Flink Kubernetes Native deployments, where Taskmanager is started on demand.
  */
final case class FlinkConfig(
    restUrl: Option[String],
    jobManagerTimeout: FiniteDuration = 1 minute,
    shouldVerifyBeforeDeploy: Boolean = true,
    shouldCheckAvailableSlots: Boolean = true,
    waitForDuringDeployFinish: FlinkWaitForDuringDeployFinishedConfig =
      FlinkWaitForDuringDeployFinishedConfig(enabled = true, Some(180), Some(1 second)),
    scenarioStateRequestTimeout: FiniteDuration = 3 seconds,
    jobConfigsCacheSize: Int = 1000,
)

object FlinkConfig {
  // Keep it synchronize with FlinkConfig
  val RestUrlPath = "restUrl"
}

final case class FlinkWaitForDuringDeployFinishedConfig(
    enabled: Boolean,
    maxChecks: Option[Int],
    delay: Option[FiniteDuration]
) {

  def toEnabledConfig: Option[EnabledFlinkWaitForDuringDeployFinishedConfig] =
    if (enabled) {
      (maxChecks, delay) match {
        case (Some(definedMaxChecks), Some(definedDelay)) =>
          Some(EnabledFlinkWaitForDuringDeployFinishedConfig(definedMaxChecks, definedDelay))
        case _ =>
          throw new IllegalArgumentException(
            s"Invalid config: $this. If you want to enable waitForDuringDeployFinish option, hou have to define both maxChecks and delay."
          )
      }
    } else {
      None
    }

}

final case class EnabledFlinkWaitForDuringDeployFinishedConfig(maxChecks: Int, delay: FiniteDuration)
