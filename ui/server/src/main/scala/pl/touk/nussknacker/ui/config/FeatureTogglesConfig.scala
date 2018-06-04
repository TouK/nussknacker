package pl.touk.nussknacker.ui.config

import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.ui.api._
import pl.touk.nussknacker.ui.process.migrate.HttpRemoteEnvironmentConfig
import pl.touk.process.report.influxdb.InfluxReporterConfig

case class FeatureTogglesConfig(development: Boolean,
                                standaloneMode: Boolean,
                                search: Option[KibanaSettings],
                                metrics: Option[GrafanaSettings],
                                remoteEnvironment: Option[HttpRemoteEnvironmentConfig],
                                counts: Option[InfluxReporterConfig],
                                environmentAlert:Option[EnvironmentAlert],
                                commentSettings: Option[CommentSettings],
                                deploySettings: Option[DeploySettings]
                               )

object FeatureTogglesConfig extends LazyLogging{
  import argonaut.ArgonautShapeless._
  import com.typesafe.config.Config
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  def create(config: Config): FeatureTogglesConfig = {
    val environmentAlert = parseOptionalConfig[EnvironmentAlert](config, "environmentAlert")
    val isDevelopmentMode = config.hasPath("developmentMode") && config.getBoolean("developmentMode")
    val standaloneModeEnabled = config.hasPath("standaloneModeEnabled") && config.getBoolean("standaloneModeEnabled")
    val metrics = parseOptionalConfig[GrafanaSettings](config, "grafanaSettings")
    val counts = parseOptionalConfig[InfluxReporterConfig](config, "grafanaSettings")
    val remoteEnvironment = parseOptionalConfig[HttpRemoteEnvironmentConfig](config, "secondaryEnvironment")
    val search = parseOptionalConfig[KibanaSettings](config, "kibanaSettings")
    val commentSettings = parseOptionalConfig[CommentSettings](config, "commentSettings")
    val deploySettings = parseOptionalConfig[DeploySettings](config, "deploySettings")
    FeatureTogglesConfig(
      development = isDevelopmentMode,
      standaloneMode = standaloneModeEnabled,
      search = search,
      metrics = metrics,
      remoteEnvironment = remoteEnvironment,
      counts = counts,
      environmentAlert = environmentAlert,
      commentSettings = commentSettings,
      deploySettings = deploySettings
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