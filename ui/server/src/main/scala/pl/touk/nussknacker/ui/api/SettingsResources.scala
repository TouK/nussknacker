package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import cats.data.Validated
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.ui.config.{AnalyticsConfig, FeatureTogglesConfig}

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
            tabs = config.tabs,
            intervalTimeSettings = config.intervalTimeSettings,
            testDataSettings = config.testDataSettings,
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

@JsonCodec case class MetricsSettings(url: String, defaultDashboard: String, scenarioTypeToDashboard: Option[Map[String, String]])

@JsonCodec case class RemoteEnvironmentConfig(targetEnvironmentId: String)

@JsonCodec case class EnvironmentAlert(content: String, cssClass: String)

@JsonCodec case class CommentSettings(substitutionPattern: String, substitutionLink: String)

case class DeploymentCommentSettings(validationPattern: String, exampleComment: Option[String])

object DeploymentCommentSettings {
  def create(validationPattern: String, exampleComment: Option[String]): Validated[EmptyDeploymentCommentSettingsError, DeploymentCommentSettings] = {
    Validated.cond(validationPattern.nonEmpty,
      new DeploymentCommentSettings(validationPattern, exampleComment),
      EmptyDeploymentCommentSettingsError("Field validationPattern cannot be empty."))
  }

  def unsafe(validationPattern: String, exampleComment: Option[String]): DeploymentCommentSettings = {
    new DeploymentCommentSettings(validationPattern, exampleComment)
  }
}

case class EmptyDeploymentCommentSettingsError(message: String) extends Exception(message)

@JsonCodec case class IntervalTimeSettings(processes: Int, healthCheck: Int)

@JsonCodec case class TestDataSettings(maxSamplesCount: Int, testDataMaxBytes: Int, resultsMaxBytes: Int)

object TopTabType extends Enumeration {

  implicit val decoder: Decoder[TopTabType.Value] = Decoder.decodeEnumeration(TopTabType)
  implicit val encoder: Encoder[TopTabType.Value] = Encoder.encodeEnumeration(TopTabType)

  val Local, Remote, IFrame, Url = Value
}

@JsonCodec case class TopTab(id: String, title: String, `type`: TopTabType.Value, url: String, requiredPermission: Option[String])

@JsonCodec case class ToggleFeaturesOptions(counts: Boolean,
                                            metrics: Option[MetricsSettings],
                                            remoteEnvironment: Option[RemoteEnvironmentConfig],
                                            environmentAlert: Option[EnvironmentAlert],
                                            commentSettings: Option[CommentSettings],
                                            tabs: Option[List[TopTab]],
                                            intervalTimeSettings: IntervalTimeSettings,
                                            testDataSettings: TestDataSettings)

@JsonCodec case class AnalyticsSettings(engine: String, url: String, siteId: String)

@JsonCodec case class AuthenticationSettings(provider: String)

@JsonCodec case class ProcessStateSettings(icons: Map[String, Map[String, String]], tooltips: Map[String, Map[String, String]])

@JsonCodec case class UISettings(features: ToggleFeaturesOptions,
                                 authentication: AuthenticationSettings,
                                 analytics: Option[AnalyticsSettings])

