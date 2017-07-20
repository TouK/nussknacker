package pl.touk.esp.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import pl.touk.esp.ui.config.FeatureTogglesConfig
import pl.touk.esp.ui.process.uiconfig.SingleNodeConfig
import pl.touk.esp.ui.security.LoggedUser
import pl.touk.http.argonaut.Argonaut62Support

import scala.concurrent.ExecutionContext

class SettingsResources(config: FeatureTogglesConfig, nodesConfig: Map[String, SingleNodeConfig])(implicit ec: ExecutionContext)
  extends Directives with Argonaut62Support with RouteWithUser {

  import argonaut.ArgonautShapeless._

  def route(implicit user: LoggedUser): Route =
    pathPrefix("settings") {
      get {
        complete {
          val toggleOptions = ToggleFeaturesOptions(
            counts = config.counts.isDefined,
            search = config.search,
            metrics = config.metrics,
            migration = config.migration.map(c => MigrationConfig(c.environmentId)),
            environmentAlert = config.environmentAlert
          )
          UISettings(toggleOptions, nodesConfig)
        }
      }
    }
}

case class GrafanaSettings(url: String, dashboard: String, env: String)
case class KibanaSettings(url: String)
case class MigrationConfig(targetEnvironmentId: String)
case class EnvironmentAlert(content: String, cssClass: String)

case class ToggleFeaturesOptions(counts: Boolean,
                                 search: Option[KibanaSettings],
                                 metrics: Option[GrafanaSettings],
                                 migration: Option[MigrationConfig],
                                 environmentAlert: Option[EnvironmentAlert]
                                )

case class UISettings(features: ToggleFeaturesOptions, nodes: Map[String, SingleNodeConfig])