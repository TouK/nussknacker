package pl.touk.nussknacker.engine.management

import cats.data.Validated
import cats.data.Validated.{invalid, valid}
import cats.implicits.catsSyntaxValidatedId
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterConfig
import pl.touk.nussknacker.engine.flink.minicluster.scenariotesting.{
  ScenarioStateVerificationConfig,
  ScenarioTestingConfig
}
import pl.touk.nussknacker.engine.management.FlinkConfig.parseRestUrl
import pl.touk.nussknacker.engine.management.rest.ParsedHttpFlinkClientConfig

import java.net.URI
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
    useMiniClusterForDeployment: Boolean = false,
    jobManagerTimeout: FiniteDuration = 1 minute,
    shouldCheckAvailableSlots: Boolean = true,
    waitForDuringDeployFinish: FlinkWaitForDuringDeployFinishedConfig =
      FlinkWaitForDuringDeployFinishedConfig(enabled = true, Some(180), Some(1 second)),
    scenarioStateRequestTimeout: FiniteDuration = 3 seconds,
    jobConfigsCacheSize: Int = 1000,
    miniCluster: FlinkMiniClusterConfig = FlinkMiniClusterConfig(),
    scenarioTesting: ScenarioTestingConfig = ScenarioTestingConfig(),
    scenarioStateVerification: ScenarioStateVerificationConfig = ScenarioStateVerificationConfig()
) extends LazyLogging {

  def parseHttpClientConfig(
      miniClusterJobManagerUrlOpt: Option[URI],
      scenarioStateCacheTTL: Option[FiniteDuration],
  ): Validated[String, ParsedHttpFlinkClientConfig] = {
    miniClusterJobManagerUrlOpt
      .map { miniClusterJobManagerUrl =>
        logger.info(
          s"useMiniClusterForDeployment is enabled, MiniCluster exposed on $miniClusterJobManagerUrl address will be used for the deployment"
        )
        miniClusterJobManagerUrl.valid
      }
      .getOrElse {
        parseRestUrl(restUrl).map { parsedUrl =>
          logger.info(
            s"useMiniClusterForDeployment is disabled, a remote Flink exposed on $parsedUrl address will be used for the deployment"
          )
          parsedUrl
        }
      }
      .map(
        ParsedHttpFlinkClientConfig(
          _,
          scenarioStateRequestTimeout,
          jobManagerTimeout,
          scenarioStateCacheTTL,
          jobConfigsCacheSize
        )
      )
  }

}

object FlinkConfig {
  // Keep it synchronize with FlinkConfig
  val RestUrlPath = "restUrl"

  def parseRestUrl(restUrlOpt: Option[String]): Validated[String, URI] =
    restUrlOpt
      .map(_.valid)
      .getOrElse("Invalid configuration: missing restUrl".invalid)
      .andThen { restUrl =>
        Try(new URI(restUrl))
          .map(valid[String, URI])
          .getOrElse(invalid[String, URI](s"Invalid configuration: restUrl is not a valid url [$restUrl]"))
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
