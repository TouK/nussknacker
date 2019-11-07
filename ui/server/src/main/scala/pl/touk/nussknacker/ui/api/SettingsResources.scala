package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.ui.config.FeatureTogglesConfig
import pl.touk.nussknacker.ui.security.AuthenticationConfig

import scala.concurrent.ExecutionContext

class SettingsResources(config: FeatureTogglesConfig,
                        typeToConfig: Map[ProcessingType, ProcessingTypeData],
                        authenticationConfig: AuthenticationConfig
                       )(implicit ec: ExecutionContext)
  extends Directives with FailFastCirceSupport with RouteWithoutUser {

  def route(): Route =
    pathPrefix("settings") {
      get {
        complete {
          val toggleOptions = ToggleFeaturesOptions(
            counts = config.counts.isDefined,
            search = config.search,
            metrics = config.metrics,
            remoteEnvironment = config.remoteEnvironment.map(c => RemoteEnvironmentConfig(c.targetEnvironmentId)),
            environmentAlert = config.environmentAlert,
            commentSettings = config.commentSettings,
            deploySettings = config.deploySettings,
            intervalTimeSettings = config.intervalTimeSettings,
            signals = signalsSupported,
            attachments = config.attachments.isDefined
          )

          val authenticationSettings = AuthenticationSettings(
            authenticationConfig.getBackend().toString,
            authenticationConfig.getAuthenticationRedirectUrl().map(_.toString)
          )

          UISettings(toggleOptions, authenticationSettings)
        }
      }
    }

  private val signalsSupported: Boolean = {
    typeToConfig.exists { case (_, processingTypeData) =>
      processingTypeData.supportsSignals
    }
  }
}

@JsonCodec case class MetricsSettings(url: String, defaultDashboard: String, processingTypeToDashboard: Option[Map[String,String]])
@JsonCodec case class KibanaSettings(url: String)
@JsonCodec case class RemoteEnvironmentConfig(targetEnvironmentId: String)
@JsonCodec case class EnvironmentAlert(content: String, cssClass: String)
@JsonCodec case class CommentSettings(matchExpression: String, link: String)
@JsonCodec case class DeploySettings(requireComment: Boolean)
@JsonCodec case class IntervalTimeSettings(processes: Int, healthCheck: Int)

@JsonCodec case class ToggleFeaturesOptions(counts: Boolean,
                                 search: Option[KibanaSettings],
                                 metrics: Option[MetricsSettings],
                                 remoteEnvironment: Option[RemoteEnvironmentConfig],
                                 environmentAlert: Option[EnvironmentAlert],
                                 commentSettings: Option[CommentSettings],
                                 deploySettings: Option[DeploySettings],
                                 intervalTimeSettings: IntervalTimeSettings,
                                 attachments: Boolean,
                                 signals: Boolean)

@JsonCodec case class AuthenticationSettings(backend: String, url: Option[String])

@JsonCodec case class UISettings(features: ToggleFeaturesOptions, authentication: AuthenticationSettings)