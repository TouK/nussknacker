package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import cats.data.OptionT
import cats.implicits.catsStdInstancesForFuture
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.ui.config.processtoolbars.ToolbarsConfigProvider
import pl.touk.nussknacker.ui.config.{AnalyticsConfig, FeatureTogglesConfig}
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.security.CertificatesAndKeys
import pl.touk.nussknacker.ui.security.api.AuthenticationConfiguration

import scala.concurrent.{ExecutionContext, Future}

class SettingsResources(val processRepository: FetchingProcessRepository[Future],
                        featureTogglesConfig: FeatureTogglesConfig,
                        typeToConfig: ProcessingTypeDataProvider[ProcessingTypeData],
                        authenticationConfig: AuthenticationConfiguration,
                        analyticsConfig: Option[AnalyticsConfig],
                        toolbarsConfigProvider: ToolbarsConfigProvider)(implicit val ec: ExecutionContext)
  extends Directives with FailFastCirceSupport with RouteWithoutUser with ProcessDirectives {

  def publicRoute(): Route =
    pathPrefix("settings") {
      get {
        complete {
          val toggleOptions = ToggleFeaturesOptions(
            counts = featureTogglesConfig.counts.isDefined,
            metrics = featureTogglesConfig.metrics,
            remoteEnvironment = featureTogglesConfig.remoteEnvironment.map(c => RemoteEnvironmentConfig(c.targetEnvironmentId)),
            environmentAlert = featureTogglesConfig.environmentAlert,
            commentSettings = featureTogglesConfig.commentSettings,
            deploySettings = featureTogglesConfig.deploySettings,
            customTabs = featureTogglesConfig.customTabs,
            intervalTimeSettings = featureTogglesConfig.intervalTimeSettings,
            signals = signalsSupported,
            attachments = featureTogglesConfig.attachments.isDefined
          )

          val authenticationSettings = AuthenticationSettings(
            authenticationConfig.method.toString,
            authenticationConfig.authorizeUrl.map(_.toString),
            authenticationConfig.authSeverPublicKey.map(CertificatesAndKeys.textualRepresentationOfPublicKey),
            authenticationConfig.idTokenNonceVerificationRequired,
            authenticationConfig.implicitGrantEnabled
          )

          val analyticsSettings = analyticsConfig.map(a => AnalyticsSettings(a.engine.toString, a.url.toString, a.siteId))
          UISettings(toggleOptions, authenticationSettings, analyticsSettings)
        }
      }
    } ~ path("process" / Segment / "settings" / "toolbars") { processName =>
      get {
        complete {
          val config = for {
            processDetails <- OptionT(processRepository.fetchProcessDetails(ProcessName(processName)))
          } yield toolbarsConfigProvider.configForCategory(processName, processDetails.processCategory, processDetails.isSubprocess)

          config.value
        }
      }
    }

  private val signalsSupported: Boolean = {
    typeToConfig.all.exists { case (_, processingTypeData) =>
      processingTypeData.supportsSignals
    }
  }
}

@JsonCodec case class MetricsSettings(url: String, defaultDashboard: String, processingTypeToDashboard: Option[Map[String,String]])
@JsonCodec case class RemoteEnvironmentConfig(targetEnvironmentId: String)
@JsonCodec case class EnvironmentAlert(content: String, cssClass: String)
@JsonCodec case class CommentSettings(matchExpression: String, link: String)
@JsonCodec case class DeploySettings(requireComment: Boolean)
@JsonCodec case class IntervalTimeSettings(processes: Int, healthCheck: Int)
@JsonCodec case class CustomTabs(name: String, url: String, id: String)

@JsonCodec case class ToggleFeaturesOptions(counts: Boolean,
                                            metrics: Option[MetricsSettings],
                                            remoteEnvironment: Option[RemoteEnvironmentConfig],
                                            environmentAlert: Option[EnvironmentAlert],
                                            commentSettings: Option[CommentSettings],
                                            deploySettings: Option[DeploySettings],
                                            customTabs: Option[List[CustomTabs]],
                                            intervalTimeSettings: IntervalTimeSettings,
                                            attachments: Boolean,
                                            signals: Boolean)

@JsonCodec case class AnalyticsSettings(engine: String, url: String, siteId: String)

@JsonCodec case class AuthenticationSettings(backend: String,
                                             authorizeUrl: Option[String],
                                             jwtAuthServerPublicKey: Option[String],
                                             jwtIdTokenNonceVerificationRequired: Boolean,
                                             implicitGrantEnabled: Boolean)

@JsonCodec case class ProcessStateSettings(icons: Map[String, Map[String, String]], tooltips: Map[String, Map[String, String]])

@JsonCodec case class UISettings(features: ToggleFeaturesOptions,
                                 authentication: AuthenticationSettings,
                                 analytics: Option[AnalyticsSettings])
