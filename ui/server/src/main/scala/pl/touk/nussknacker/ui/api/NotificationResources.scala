package pl.touk.nussknacker.ui.api

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.Materializer
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.process.deployment._
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import akka.pattern.ask
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Decoder, Encoder}
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository

class NotificationResources(managementActor: ActorRef)
                           (implicit ec: ExecutionContext, mat: Materializer, system: ActorSystem)
  extends Directives
    with LazyLogging
    with RouteWithUser with FailFastCirceSupport {

  //TODO: in the future we could use https://github.com/akka/akka-http/pull/1828 when we can bump version to 10.1.x
  private val durationFromConfig = system.settings.config.getDuration("akka.http.server.request-timeout")
  private implicit val timeout: Timeout = Timeout(durationFromConfig.toMillis millis)

  def securedRoute(implicit user: LoggedUser): Route = {
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
        case DeploymentStatusResponse(deploymentInfos) =>
          deploymentInfos.map{ case (k, v) => toNotification(k, v) }.toList
      }
  }

  //TODO: consider 'personalization' - different message for user who is deploying
  private def toNotification(processName: ProcessName, deploymentInfo: DeployInfo): Notification = {
    val actionString = deploymentInfo.action match {
      case DeploymentActionType.Deployment => "deployed"
      case DeploymentActionType.Cancel => "cancelled"
    }
    Notification(s"Scenario ${processName.value} is being $actionString by ${deploymentInfo.userId}", NotificationType.info)
  }

}

@JsonCodec case class Notification(message: String, `type`: NotificationType.Value)

object NotificationType extends Enumeration {

  implicit val typeEncoder: Encoder[NotificationType.Value] = Encoder.enumEncoder(NotificationType)
  implicit val typeDecoder: Decoder[NotificationType.Value] = Decoder.enumDecoder(NotificationType)

  type NotificationType = Value
  val info, warning = Value
}