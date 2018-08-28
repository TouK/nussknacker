package pl.touk.nussknacker.ui.api

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.Materializer
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.process.deployment.{DeployInfo, DeploymentStatus, DeploymentStatusResponse}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import akka.pattern.ask
import pl.touk.nussknacker.engine.util.json.Codecs
import pl.touk.nussknacker.ui.util.Argonaut62Support
import argonaut.ArgonautShapeless._
import argonaut.CodecJson

import scala.util.Random

class NotificationResources(managementActor: ActorRef)(implicit ec: ExecutionContext, mat: Materializer, system: ActorSystem)
  extends Directives
    with LazyLogging
    with RouteWithUser with Argonaut62Support {

  //TODO: in the future we could use https://github.com/akka/akka-http/pull/1828 when we can bump version to 10.1.x
  private val durationFromConfig = system.settings.config.getDuration("akka.http.server.request-timeout")
  private implicit val timeout: Timeout = Timeout(durationFromConfig.toMillis millis)
  private implicit val typeCodec: CodecJson[NotificationType.Value] = Codecs.enumCodec(NotificationType)

  def route(implicit user: LoggedUser): Route = {
    path("notifications") {
      get {
        complete {
          //TODO: add different notifications?
          prepareDeploymentNotifications()
        }
      }
    }
  }

  private def prepareDeploymentNotifications(): Future[List[Notification]] = {
    (managementActor ? DeploymentStatus)
      .mapTo[DeploymentStatusResponse]
      .map {
        case DeploymentStatusResponse(deploymentInfos) => deploymentInfos.map{ case (k, v) => toNotification(k, v) }.toList
      }
  }

  //TODO: consider 'personalization' - different message for user who is deploying
  private def toNotification(processId: String, deploymentInfo: DeployInfo): Notification = {
    Notification(s"Process $processId is ${deploymentInfo.action.toLowerCase} by ${deploymentInfo.userId}", NotificationType.info)
  }

}

case class Notification(message: String, `type`: NotificationType.Value)

object NotificationType extends Enumeration {
  type NotificationType = Value
  val info, warning = Value
}