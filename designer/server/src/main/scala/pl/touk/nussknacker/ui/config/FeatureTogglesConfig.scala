package pl.touk.nussknacker.ui.config

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.engine.util.config.FicusReaders
import pl.touk.nussknacker.ui.api._
import pl.touk.nussknacker.ui.config.Implicits.parseOptionalConfig
import pl.touk.nussknacker.ui.process.migrate.HttpRemoteEnvironmentConfig

import scala.concurrent.duration.FiniteDuration

case class FeatureTogglesConfig(development: Boolean,
                                metrics: Option[MetricsSettings],
                                remoteEnvironment: Option[HttpRemoteEnvironmentConfig],
                                counts: Option[Config],
                                environmentAlert:Option[EnvironmentAlert],
                                commentSettings: Option[CommentSettings],
                                deploymentCommentSettings: Option[DeploymentCommentSettings],
                                scenarioStateTimeout: Option[FiniteDuration],
                                surveySettings: Option[SurveySettings],
                                tabs: Option[List[TopTab]],
                                intervalTimeSettings: IntervalTimeSettings,
                                testDataSettings: TestDataSettings,
                                enableConfigEndpoint: Boolean,
                                redirectAfterArchive: Boolean,
                                codeSuggestionsFromBackend: Boolean,
                               )

object FeatureTogglesConfig extends LazyLogging{
  import com.typesafe.config.Config
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  def create(config: Config): FeatureTogglesConfig = {
    val environmentAlert = parseOptionalConfig[EnvironmentAlert](config, "environmentAlert")
    val isDevelopmentMode = config.hasPath("developmentMode") && config.getBoolean("developmentMode")
    val enableConfigEndpoint = config.hasPath("enableConfigEndpoint") && config.getBoolean("enableConfigEndpoint")
    val metrics = parseOptionalConfig[MetricsSettings](config, "metricsSettings")
      .orElse(parseOptionalConfig[MetricsSettings](config, "grafanaSettings"))
    val counts = parseOptionalConfig[Config](config, "countsSettings")

    val remoteEnvironment = parseOptionalConfig[HttpRemoteEnvironmentConfig](config, "secondaryEnvironment")
    val commentSettings = parseOptionalConfig[CommentSettings](config, "commentSettings")
    val deploymentCommentSettings = parseOptionalConfig[DeploymentCommentSettings](config, "deploymentCommentSettings")
    val scenarioStateTimeout = parseOptionalConfig[FiniteDuration](config, "scenarioStateTimeout")
    val surveySettings = parseOptionalConfig[SurveySettings](config, "surveySettings")

    implicit val tabDecoder: ValueReader[TopTab] = FicusReaders.forDecoder
    val tabs = parseOptionalConfig[List[TopTab]](config, "tabs")
    val intervalTimeSettings = config.as[IntervalTimeSettings]("intervalTimeSettings")
    val testDataSettings = config.as[TestDataSettings]("testDataSettings")
    val redirectAfterArchive = config.getAs[Boolean]("redirectAfterArchive").getOrElse(true)
    val codeSuggestionsFromBackend = config.getAs[Boolean]("codeSuggestionsFromBackend").getOrElse(false)

    FeatureTogglesConfig(
      development = isDevelopmentMode,
      metrics = metrics,
      remoteEnvironment = remoteEnvironment,
      counts = counts,
      commentSettings = commentSettings,
      deploymentCommentSettings = deploymentCommentSettings,
      scenarioStateTimeout = scenarioStateTimeout,
      surveySettings = surveySettings,
      tabs = tabs,
      intervalTimeSettings = intervalTimeSettings,
      environmentAlert = environmentAlert,
      testDataSettings = testDataSettings,
      enableConfigEndpoint = enableConfigEndpoint,
      redirectAfterArchive = redirectAfterArchive,
      codeSuggestionsFromBackend = codeSuggestionsFromBackend,
    )
  }

}