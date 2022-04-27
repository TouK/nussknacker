package pl.touk.nussknacker.ui.config

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.engine.util.config.FicusReaders
import pl.touk.nussknacker.ui.api._
import pl.touk.nussknacker.ui.config.Implicits.parseOptionalConfig
import pl.touk.nussknacker.ui.process.migrate.HttpRemoteEnvironmentConfig

case class FeatureTogglesConfig(development: Boolean,
                                metrics: Option[MetricsSettings],
                                remoteEnvironment: Option[HttpRemoteEnvironmentConfig],
                                counts: Option[Config],
                                environmentAlert:Option[EnvironmentAlert],
                                commentSettings: Option[CommentSettings],
                                deploySettings: Option[DeploySettings],
                                tabs: Option[List[TopTab]],
                                intervalTimeSettings: IntervalTimeSettings,
                                testDataSettings: TestDataSettings
                               )

object FeatureTogglesConfig extends LazyLogging{
  import com.typesafe.config.Config
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  def create(config: Config): FeatureTogglesConfig = {
    val environmentAlert = parseOptionalConfig[EnvironmentAlert](config, "environmentAlert")
    val isDevelopmentMode = config.hasPath("developmentMode") && config.getBoolean("developmentMode")
    val metrics = parseOptionalConfig[MetricsSettings](config, "metricsSettings")
      .orElse(parseOptionalConfig[MetricsSettings](config, "grafanaSettings"))
    val counts = parseOptionalConfig[Config](config, "countsSettings")

    val remoteEnvironment = parseOptionalConfig[HttpRemoteEnvironmentConfig](config, "secondaryEnvironment")
    val commentSettings = parseOptionalConfig[CommentSettings](config, "commentSettings")
    val deploySettings = parseOptionalConfig[DeploySettings](config, "deploySettings")

    implicit val tabDecoder: ValueReader[TopTab] = FicusReaders.forDecoder
    val tabs = parseOptionalConfig[List[TopTab]](config, "tabs")
    val intervalTimeSettings = config.as[IntervalTimeSettings]("intervalTimeSettings")
    val testDataSettings = config.as[TestDataSettings]("testDataSettings")

    FeatureTogglesConfig(
      development = isDevelopmentMode,
      metrics = metrics,
      remoteEnvironment = remoteEnvironment,
      counts = counts,
      commentSettings = commentSettings,
      deploySettings = deploySettings,
      tabs = tabs,
      intervalTimeSettings = intervalTimeSettings,
      environmentAlert = environmentAlert,
      testDataSettings = testDataSettings,
    )
  }

}