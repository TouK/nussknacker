package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import pl.touk.nussknacker.ui.config.FeatureTogglesConfig
import pl.touk.http.argonaut.{Argonaut62Support, JsonMarshaller}
import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext

class SettingsResources(config: FeatureTogglesConfig, typeToConfig: Map[ProcessingType, ProcessingTypeData])(implicit ec: ExecutionContext, jsonMarshaller: JsonMarshaller)
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
            remoteEnvironment = config.remoteEnvironment.map(c => RemoteEnvironmentConfig(c.targetEnvironmentId)),
            environmentAlert = config.environmentAlert,
            commentSettings = config.commentSettings,
            deploySettings = config.deploySettings,
            intervalSettings = config.intervalSettings.getOrElse(IntervalSettings.baseIntervalSettings),
            signals = signalsSupported,
            attachments = config.attachments.isDefined
          )
          UISettings(toggleOptions)
        }
      }
    }

  private val signalsSupported: Boolean = {
    typeToConfig.exists { case (_, processingTypeData) =>
      processingTypeData.supportsSignals
    }
  }
}

case class MetricsSettings(url: String, defaultDashboard: String, processingTypeToDashboard: Option[Map[String,String]])
case class KibanaSettings(url: String)
case class RemoteEnvironmentConfig(targetEnvironmentId: String)
case class EnvironmentAlert(content: String, cssClass: String)
case class CommentSettings(matchExpression: String, link: String)
case class DeploySettings(requireComment: Boolean)
case class IntervalSettings(base: Int, processes: Int, healthCheck: Int)

object IntervalSettings {
  val intervalBase = 15000
  val intervalProcesses = 20000
  val intervalHealthCheck = 30000

  def baseIntervalSettings: IntervalSettings = IntervalSettings(intervalBase, intervalProcesses, intervalHealthCheck)
}

case class ToggleFeaturesOptions(counts: Boolean,
                                 search: Option[KibanaSettings],
                                 metrics: Option[MetricsSettings],
                                 remoteEnvironment: Option[RemoteEnvironmentConfig],
                                 environmentAlert: Option[EnvironmentAlert],
                                 commentSettings: Option[CommentSettings],
                                 deploySettings: Option[DeploySettings],
                                 intervalSettings: IntervalSettings,
                                 attachments: Boolean,
                                 signals: Boolean)


case class UISettings(features: ToggleFeaturesOptions)