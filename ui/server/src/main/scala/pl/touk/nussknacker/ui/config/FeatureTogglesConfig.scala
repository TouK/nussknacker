package pl.touk.nussknacker.ui.config

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.ui.api._
import pl.touk.nussknacker.ui.process.migrate.HttpRemoteEnvironmentConfig

case class FeatureTogglesConfig(development: Boolean,
                                standaloneMode: Boolean,
                                search: Option[KibanaSettings],
                                metrics: Option[MetricsSettings],
                                remoteEnvironment: Option[HttpRemoteEnvironmentConfig],
                                counts: Option[Config],
                                environmentAlert:Option[EnvironmentAlert],
                                commentSettings: Option[CommentSettings],
                                deploySettings: Option[DeploySettings],
                                intervalSettings: Option[IntervalSettings],
                                attachments: Option[String])

object FeatureTogglesConfig extends LazyLogging{
  import argonaut.ArgonautShapeless._
  import com.typesafe.config.Config
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  def create(config: Config): FeatureTogglesConfig = {
    val environmentAlert = parseOptionalConfig[EnvironmentAlert](config, "environmentAlert")
    val isDevelopmentMode = config.hasPath("developmentMode") && config.getBoolean("developmentMode")
    val standaloneModeEnabled = config.hasPath("standaloneModeEnabled") && config.getBoolean("standaloneModeEnabled")
    val metrics = parseOptionalConfig[MetricsSettings](config, "metricsSettings")
      .orElse(parseOptionalConfig[MetricsSettings](config, "grafanaSettings"))
    val counts = parseOptionalConfig[Config](config, "countsSettings")

    val remoteEnvironment = parseOptionalConfig[HttpRemoteEnvironmentConfig](config, "secondaryEnvironment")
    val search = parseOptionalConfig[KibanaSettings](config, "kibanaSettings")
    val commentSettings = parseOptionalConfig[CommentSettings](config, "commentSettings")
    val deploySettings = parseOptionalConfig[DeploySettings](config, "deploySettings")
    val attachments = parseOptionalConfig[String](config, "attachmentsPath")
    val intervalSettings = parseOptionalConfig[IntervalSettings](config, "intervalSettings")

    FeatureTogglesConfig(
      development = isDevelopmentMode,
      standaloneMode = standaloneModeEnabled,
      search = search,
      metrics = metrics,
      remoteEnvironment = remoteEnvironment,
      counts = counts,
      commentSettings = commentSettings,
      deploySettings = deploySettings,
      intervalSettings = intervalSettings,
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