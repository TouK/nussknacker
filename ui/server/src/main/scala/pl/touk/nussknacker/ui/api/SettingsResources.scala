package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.ui.config.{AnalyticsConfig, FeatureTogglesConfig}
import pl.touk.nussknacker.engine.api.CirceUtil._

import scala.concurrent.ExecutionContext

class SettingsResources(config: FeatureTogglesConfig,
                        authenticationMethod: String,
                        analyticsConfig: Option[AnalyticsConfig])(implicit ec: ExecutionContext)
  extends Directives with FailFastCirceSupport with RouteWithoutUser {

  def publicRoute(): Route =
    pathPrefix("settings") {
      get {
        complete {
          val toggleOptions = ToggleFeaturesOptions(
            counts = config.counts.isDefined,
            metrics = config.metrics,
            remoteEnvironment = config.remoteEnvironment.map(c => RemoteEnvironmentConfig(c.targetEnvironmentId)),
            environmentAlert = config.environmentAlert,
            commentSettings = config.commentSettings,
            deploySettings = config.deploySettings,
            tabs = config.tabs,
            intervalTimeSettings = config.intervalTimeSettings,
            attachments = config.attachments.isDefined
          )

          val authenticationSettings = AuthenticationSettings(
            authenticationMethod
          )

          val analyticsSettings = analyticsConfig.map(a => AnalyticsSettings(a.engine.toString, a.url.toString, a.siteId))
          UISettings(toggleOptions, authenticationSettings, analyticsSettings)
        }
      }
    }

}

@JsonCodec case class MetricsSettings(url: String, defaultDashboard: String, processingTypeToDashboard: Option[Map[String, String]])

@JsonCodec case class RemoteEnvironmentConfig(targetEnvironmentId: String)

@JsonCodec case class EnvironmentAlert(content: String, cssClass: String)

@JsonCodec case class CommentSettings(matchExpression: String, link: String)

@JsonCodec case class DeploySettings(requireComment: Boolean)

@JsonCodec case class IntervalTimeSettings(processes: Int, healthCheck: Int)

object TopTabType extends Enumeration {

  implicit val decoder: Decoder[TopTabType.Value] = Decoder.enumDecoder(TopTabType)
  implicit val encoder: Encoder[TopTabType.Value] = Encoder.enumEncoder(TopTabType)

  val Local, Remote, IFrame = Value
}

@JsonCodec case class TopTab(id: String, title: String, `type`: TopTabType.Value, url: String, requiredPermission: Option[String])

@JsonCodec case class ToggleFeaturesOptions(counts: Boolean,
                                            metrics: Option[MetricsSettings],
                                            remoteEnvironment: Option[RemoteEnvironmentConfig],
                                            environmentAlert: Option[EnvironmentAlert],
                                            commentSettings: Option[CommentSettings],
                                            deploySettings: Option[DeploySettings],
                                            tabs: Option[List[TopTab]],
                                            intervalTimeSettings: IntervalTimeSettings,
                                            attachments: Boolean)

@JsonCodec case class AnalyticsSettings(engine: String, url: String, siteId: String)

@JsonCodec case class AuthenticationSettings(provider: String)

@JsonCodec case class ProcessStateSettings(icons: Map[String, Map[String, String]], tooltips: Map[String, Map[String, String]])

@JsonCodec case class UISettings(features: ToggleFeaturesOptions,
                                 authentication: AuthenticationSettings,
                                 analytics: Option[AnalyticsSettings])
