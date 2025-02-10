package pl.touk.nussknacker.engine.management

import cats.data.Validated
import cats.data.Validated.{invalid, valid}
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterConfig
import pl.touk.nussknacker.engine.flink.minicluster.scenariotesting.{
  ScenarioStateVerificationConfig,
  ScenarioTestingConfig
}
import pl.touk.nussknacker.engine.management.FlinkConfig.parseRestUrl
import pl.touk.nussknacker.engine.management.rest.{HttpFlinkClient, ParsedHttpFlinkClientConfig}

import java.net.URL
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.Try

/**
  * FlinkConfig deployment configuration.
  *
  * @param restUrl REST API endpoint of the Flink cluster.
  * @param jobManagerTimeout Timeout for communication with FLink cluster. Consider extending if e.g. you have long savepoint times etc.
  * @param shouldCheckAvailableSlots When true {@link FlinkDeploymentManager} checks if there are free slots to run new job. This check should be disabled on Flink Kubernetes Native deployments, where Taskmanager is started on demand.
  */
final case class FlinkConfig(
    restUrl: Option[String],
    jobManagerTimeout: FiniteDuration = 1 minute,
    shouldCheckAvailableSlots: Boolean = true,
    waitForDuringDeployFinish: FlinkWaitForDuringDeployFinishedConfig =
      FlinkWaitForDuringDeployFinishedConfig(enabled = true, Some(180), Some(1 second)),
    scenarioStateRequestTimeout: FiniteDuration = 3 seconds,
    jobConfigsCacheSize: Int = 1000,
    miniCluster: FlinkMiniClusterConfig = FlinkMiniClusterConfig(),
    scenarioTesting: ScenarioTestingConfig = ScenarioTestingConfig(),
    scenarioStateVerification: ScenarioStateVerificationConfig = ScenarioStateVerificationConfig()
) {

  def parseHttpClientConfig(
      scenarioStateCacheTTL: Option[FiniteDuration]
  ): Validated[String, ParsedHttpFlinkClientConfig] = parseRestUrl(restUrl).map(
    ParsedHttpFlinkClientConfig(
      _,
      scenarioStateRequestTimeout,
      jobManagerTimeout,
      scenarioStateCacheTTL,
      jobConfigsCacheSize
    )
  )

}

object FlinkConfig {
  // Keep it synchronize with FlinkConfig
  val RestUrlPath = "restUrl"

  def parseRestUrl(restUrlOpt: Option[String]): Validated[String, URL] =
    restUrlOpt
      .map(valid[String, String])
      .getOrElse(invalid[String, String]("Invalid configuration: missing restUrl"))
      .andThen { restUrl =>
        Try(new URL(restUrl))
          .map(valid[String, URL])
          .getOrElse(invalid[String, URL](s"Invalid configuration: restUrl is not a valid url [$restUrl]"))
      }

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
