package pl.touk.esp.ui.api

import akka.actor.ActorRef
import akka.http.scaladsl.model.{HttpResponse, StatusCode, StatusCodes}
import akka.http.scaladsl.server._
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.runtime.messages.JobClientMessages.JobManagerActorRef
import pl.touk.esp.ui.process.deployment.CheckStatus
import pl.touk.esp.ui.process.displayedgraph.ProcessStatus
import pl.touk.esp.ui.process.repository.ProcessRepository
import pl.touk.esp.ui.process.repository.ProcessRepository.ProcessDetails
import pl.touk.esp.ui.security.LoggedUser
import pl.touk.esp.ui.util.Argonaut62Support

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class AppResources(buildInfo: Map[String, String],
                   processRepository: ProcessRepository,
                   managerActor: ActorRef)(implicit ec: ExecutionContext)
  extends Directives with Argonaut62Support with LazyLogging {

  import argonaut.ArgonautShapeless._

  def route(implicit user: LoggedUser): Route =
    pathPrefix("app") {
      path("buildInfo") {
        get {
          complete {
            buildInfo
          }
        }
      } ~
      path("healthCheck")  {
        get {
          complete {
            notRunningProcessesThatShouldRun.map[HttpResponse] { set =>
              if (set.isEmpty) {
                HttpResponse(status = StatusCodes.OK)
              } else {
                logger.warn(s"Processes not running: $set")
                HttpResponse(status = StatusCodes.InternalServerError, entity = s"Processes not running: ${set.mkString(",")}")
              }
            }.recover[HttpResponse] {
              case NonFatal(e) =>
                logger.error("Failed to get statuses", e)
                HttpResponse(status = StatusCodes.InternalServerError, entity = "Failed to retrieve job statuses")
            }
          }
        }
      }
    }


  private def notRunningProcessesThatShouldRun(implicit ec: ExecutionContext, user: LoggedUser) : Future[Set[String]] = {

    for {
      processes <- processRepository.fetchProcessesDetails()
      statusMap <- Future.sequence(statusList(processes)).map(_.toMap)
    } yield {
      statusMap.filter { case (_, status) => !status.exists(_.isRunning) }.keySet
    }
  }

  private def statusList(processes: Seq[ProcessDetails]) : Seq[Future[(String, Option[ProcessStatus])]] =
    processes.filterNot(_.currentlyDeployedAt.isEmpty).map(_.name).map(n => findJobStatus(n).map((n, _)))

  private def findJobStatus(processName: String)(implicit ec: ExecutionContext): Future[Option[ProcessStatus]] = {
    import scala.concurrent.duration._
    import akka.pattern.ask
    implicit val timeout = Timeout(1 minute)
    (managerActor ? CheckStatus(processName)).mapTo[Option[ProcessStatus]]
  }
}
