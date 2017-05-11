package pl.touk.esp.ui.config

import pl.touk.esp.ui.EspUiApp.config
import pl.touk.esp.ui.api.{GrafanaSettings, KibanaSettings}
import pl.touk.esp.ui.process.migrate.HttpMigratorTargetEnvironmentConfig
import pl.touk.process.report.influxdb.InfluxReporterConfig

import scala.util.Try

case class FeatureTogglesConfig(development: Boolean,
                                standaloneMode: Boolean,
                                search: Option[KibanaSettings],
                                metrics: Option[GrafanaSettings],
                                migration: Option[HttpMigratorTargetEnvironmentConfig],
                                counts: Option[InfluxReporterConfig],
                                environmentAlert:Option[String])

object FeatureTogglesConfig {
  import com.typesafe.config.Config
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import argonaut.ArgonautShapeless._
  def create(config: Config, environment: String): FeatureTogglesConfig = {
    val environmentAlert = Try(config.getString("environmentAlert")).toOption
    val isDevelopmentMode = config.hasPath("developmentMode") && config.getBoolean("developmentMode")
    val standaloneModeEnabled = config.hasPath("standaloneModeEnabled") && config.getBoolean("standaloneModeEnabled")
    val metrics = Try(config.as[GrafanaSettings]("grafanaSettings")).toOption
    val counts = Try(config.as[InfluxReporterConfig]("grafanaSettings")).toOption
    val migration = parseMigrationConfig(config, environment)
    val search = Try(config.as[KibanaSettings]("kibanaSettings")).toOption
    FeatureTogglesConfig(
      development = isDevelopmentMode,
      standaloneMode = standaloneModeEnabled,
      search = search,
      metrics = metrics,
      migration = migration,
      counts = counts,
      environmentAlert=environmentAlert
    )
  }

  private def parseMigrationConfig(config: Config, environment: String) = {
    val key = "secondaryEnvironment"
    if (config.hasPath(key)) Some(config.as[HttpMigratorTargetEnvironmentConfig](key)) else None
  }
}