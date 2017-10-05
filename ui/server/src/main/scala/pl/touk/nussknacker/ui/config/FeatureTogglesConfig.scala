package pl.touk.nussknacker.ui.config

import pl.touk.nussknacker.ui.api.{EnvironmentAlert, GrafanaSettings, KibanaSettings}
import pl.touk.nussknacker.ui.process.migrate.HttpRemoteEnvironmentConfig
import pl.touk.process.report.influxdb.InfluxReporterConfig

import scala.util.Try

case class FeatureTogglesConfig(development: Boolean,
                                standaloneMode: Boolean,
                                search: Option[KibanaSettings],
                                metrics: Option[GrafanaSettings],
                                remoteEnvironment: Option[HttpRemoteEnvironmentConfig],
                                counts: Option[InfluxReporterConfig],
                                environmentAlert:Option[EnvironmentAlert]
                               )

object FeatureTogglesConfig {
  import argonaut.ArgonautShapeless._
  import com.typesafe.config.Config
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  def create(config: Config, environment: String): FeatureTogglesConfig = {
    val environmentAlert = Try(config.as[EnvironmentAlert]("environmentAlert")).toOption
    val isDevelopmentMode = config.hasPath("developmentMode") && config.getBoolean("developmentMode")
    val standaloneModeEnabled = config.hasPath("standaloneModeEnabled") && config.getBoolean("standaloneModeEnabled")
    val metrics = Try(config.as[GrafanaSettings]("grafanaSettings")).toOption
    val counts = Try(config.as[InfluxReporterConfig]("grafanaSettings")).toOption
    val remoteEnvironment = parseRemoteEnvironmentConfig(config, environment)
    val search = Try(config.as[KibanaSettings]("kibanaSettings")).toOption
    FeatureTogglesConfig(
      development = isDevelopmentMode,
      standaloneMode = standaloneModeEnabled,
      search = search,
      metrics = metrics,
      remoteEnvironment = remoteEnvironment,
      counts = counts,
      environmentAlert=environmentAlert
    )
  }

  private def parseRemoteEnvironmentConfig(config: Config, environment: String) = {
    val key = "secondaryEnvironment"
    if (config.hasPath(key)) Some(config.as[HttpRemoteEnvironmentConfig](key)) else None
  }
}