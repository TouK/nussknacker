package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import pl.touk.nussknacker.ui.config.FeatureTogglesConfig
import pl.touk.nussknacker.ui.process.uiconfig.SingleNodeConfig
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.nussknacker.ui.security.api.LoggedUser

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
            remoteEnvironment = config.remoteEnvironment.map(c => RemoteEnvironmentConfig(c.environmentId)),
            environmentAlert = config.environmentAlert,
            commentSettings = config.commentSettings,
            deploySettings = config.deploySettings,
            signals = config.queryableStateProxyUrl.isDefined,
            attachments = config.attachments.isDefined,
            additionalPropertiesLabels = config.additionalPropertiesLabels
          )
          UISettings(toggleOptions, nodesConfig)
        }
      }
    }
}

case class MetricsSettings(url: String, defaultDashboard: String, processingTypeToDashboard: Option[Map[String,String]])
case class KibanaSettings(url: String)
case class RemoteEnvironmentConfig(targetEnvironmentId: String)
case class EnvironmentAlert(content: String, cssClass: String)
case class CommentSettings(matchExpression: String, link: String)
case class DeploySettings(requireComment: Boolean)

case class ToggleFeaturesOptions(counts: Boolean,
                                 search: Option[KibanaSettings],
                                 metrics: Option[MetricsSettings],
                                 remoteEnvironment: Option[RemoteEnvironmentConfig],
                                 environmentAlert: Option[EnvironmentAlert],
                                 commentSettings: Option[CommentSettings],
                                 deploySettings: Option[DeploySettings],
                                 attachments: Boolean,
                                 signals: Boolean,
                                 additionalPropertiesLabels: Map[String, String]
                                )

case class UISettings(features: ToggleFeaturesOptions, nodes: Map[String, SingleNodeConfig])