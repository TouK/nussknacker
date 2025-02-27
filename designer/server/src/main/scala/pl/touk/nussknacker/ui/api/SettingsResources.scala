package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import cats.data.Validated
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.CirceUtil.codecs._
import pl.touk.nussknacker.ui.api.description.stickynotes.Dtos.StickyNotesSettings
import pl.touk.nussknacker.ui.config.{FeatureTogglesConfig, UsageStatisticsReportsConfig}
import pl.touk.nussknacker.ui.statistics.{Fingerprint, FingerprintService}

import java.net.URL
import scala.concurrent.ExecutionContext

class SettingsResources(
    config: FeatureTogglesConfig,
    authenticationMethod: String,
    usageStatisticsReportsConfig: UsageStatisticsReportsConfig,
    fingerprintService: FingerprintService
)(implicit ec: ExecutionContext)
    extends Directives
    with FailFastCirceSupport
    with RouteWithoutUser {

  def publicRoute(): Route =
    pathPrefix("settings") {
      get {
        complete {
          fingerprintService
            .fingerprint(usageStatisticsReportsConfig)
            .map { fingerprint =>
              val toggleOptions = ToggleFeaturesOptions(
                counts = config.counts.isDefined,
                metrics = config.metrics,
                remoteEnvironment = config.remoteEnvironment.map(c => RemoteEnvironmentConfig(c.targetEnvironmentId)),
                environmentAlert = config.environmentAlert,
                commentSettings = config.commentSettings,
                deploymentCommentSettings = config.deploymentCommentSettings,
                surveySettings = config.surveySettings,
                tabs = config.tabs,
                intervalTimeSettings = config.intervalTimeSettings,
                testDataSettings = config.testDataSettings,
                redirectAfterArchive = config.redirectAfterArchive,
                usageStatisticsReports =
                  UsageStatisticsReportsSettings(usageStatisticsReportsConfig, fingerprint.toOption),
                stickyNotesSettings = config.stickyNotesSettings
              )
              val authenticationSettings = AuthenticationSettings(
                authenticationMethod
              )
              UISettings(toggleOptions, authenticationSettings)
            }
        }
      }
    }

}

@JsonCodec final case class MetricsSettings(
    url: String,
    defaultDashboard: String,
    scenarioTypeToDashboard: Option[Map[String, String]]
)

@JsonCodec final case class RemoteEnvironmentConfig(targetEnvironmentId: String)

@JsonCodec final case class EnvironmentAlert(content: String, color: String)

@JsonCodec final case class CommentSettings(substitutionPattern: String, substitutionLink: String)

@JsonCodec final case class DeploymentCommentSettings(validationPattern: String, exampleComment: Option[String])

@JsonCodec final case class SurveySettings(key: String, text: String, link: URL)

object DeploymentCommentSettings {

  def create(
      validationPattern: String,
      exampleComment: Option[String]
  ): Validated[EmptyDeploymentCommentSettingsError, DeploymentCommentSettings] = {
    Validated.cond(
      validationPattern.nonEmpty,
      new DeploymentCommentSettings(validationPattern, exampleComment),
      EmptyDeploymentCommentSettingsError("Field validationPattern cannot be empty.")
    )
  }

  def unsafe(validationPattern: String, exampleComment: Option[String]): DeploymentCommentSettings = {
    new DeploymentCommentSettings(validationPattern, exampleComment)
  }

}

final case class EmptyDeploymentCommentSettingsError(message: String) extends Exception(message)

@JsonCodec final case class IntervalTimeSettings(processes: Int, healthCheck: Int)

@JsonCodec final case class TestDataSettings(maxSamplesCount: Int, testDataMaxLength: Int, resultsMaxBytes: Int)

object TopTabType extends Enumeration {

  implicit val decoder: Decoder[TopTabType.Value] = Decoder.decodeEnumeration(TopTabType)
  implicit val encoder: Encoder[TopTabType.Value] = Encoder.encodeEnumeration(TopTabType)

  val Local, Remote, IFrame, Url = Value
}

@JsonCodec final case class TopTab(
    id: String,
    title: Option[String],
    `type`: TopTabType.Value,
    url: String,
    requiredPermission: Option[String],
    // Deprecated: use accessTokenInQuery.enabled setting instead
    addAccessTokenInQueryParam: Option[Boolean],
    accessTokenInQuery: Option[AccessTokenInQueryTabSettings] = Some(AccessTokenInQueryTabSettings()),
    spacerBefore: Option[Boolean] = Some(false)
)

@JsonCodec final case class AccessTokenInQueryTabSettings(
    enabled: Boolean = false,
    // The default parameter name is consistent with parameter name used by Grafana:
    // https://grafana.com/docs/grafana/latest/setup-grafana/configure-security/configure-authentication/jwt/?plcmt=learn-nav#url-login
    parameterName: Option[String] = Some("auth_token")
)

@JsonCodec final case class ToggleFeaturesOptions(
    counts: Boolean,
    metrics: Option[MetricsSettings],
    remoteEnvironment: Option[RemoteEnvironmentConfig],
    environmentAlert: Option[EnvironmentAlert],
    commentSettings: Option[CommentSettings],
    deploymentCommentSettings: Option[DeploymentCommentSettings],
    surveySettings: Option[SurveySettings],
    tabs: Option[List[TopTab]],
    intervalTimeSettings: IntervalTimeSettings,
    testDataSettings: TestDataSettings,
    stickyNotesSettings: StickyNotesSettings,
    redirectAfterArchive: Boolean,
    usageStatisticsReports: UsageStatisticsReportsSettings,
)

@JsonCodec final case class AuthenticationSettings(provider: String)

@JsonCodec final case class UISettings(
    features: ToggleFeaturesOptions,
    authentication: AuthenticationSettings
)

@JsonCodec final case class UsageStatisticsReportsSettings(
    enabled: Boolean,
    errorReportsEnabled: Boolean,
    fingerprint: Option[String]
)

object UsageStatisticsReportsSettings {

  def apply(
      usageStatisticsReportsConfig: UsageStatisticsReportsConfig,
      maybeFingerprint: Option[Fingerprint]
  ): UsageStatisticsReportsSettings =
    UsageStatisticsReportsSettings(
      enabled = usageStatisticsReportsConfig.enabled,
      errorReportsEnabled = usageStatisticsReportsConfig.errorReportsEnabled,
      fingerprint = maybeFingerprint.map(_.value)
    )

}
