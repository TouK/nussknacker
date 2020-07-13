package pl.touk.nussknacker.ui.config

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.ui.api._
import pl.touk.nussknacker.ui.process.migrate.HttpRemoteEnvironmentConfig

case class FeatureTogglesConfig(development: Boolean,
                                metrics: Option[MetricsSettings],
                                remoteEnvironment: Option[HttpRemoteEnvironmentConfig],
                                counts: Option[Config],
                                environmentAlert:Option[EnvironmentAlert],
                                commentSettings: Option[CommentSettings],
                                deploySettings: Option[DeploySettings],
                                customTabs: Option[List[CustomTabs]],
                                intervalTimeSettings: IntervalTimeSettings,
                                attachments: Option[String])

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
    val customTabs = parseOptionalConfig[List[CustomTabs]](config, "customTabs")
    val attachments = parseOptionalConfig[String](config, "attachmentsPath")
    val intervalTimeSettings = config.as[IntervalTimeSettings]("intervalTimeSettings")

    FeatureTogglesConfig(
      development = isDevelopmentMode,
      metrics = metrics,
      remoteEnvironment = remoteEnvironment,
      counts = counts,
      commentSettings = commentSettings,
      deploySettings = deploySettings,
      customTabs = customTabs,
      intervalTimeSettings = intervalTimeSettings,
      environmentAlert = environmentAlert,
      attachments = attachments
    )
  }

  private def parseOptionalConfig[T](config: Config,path: String)(implicit reader: ValueReader[T]): Option[T] = {
    if(config.hasPath(path)) {
      logger.debug(s"Found optional config at path=$path, parsing...")
      Some(config.as[T](path))
    } else {
      logger.debug(s"Optional config at path=$path not found, skipping.")
      None
    }
  }

}