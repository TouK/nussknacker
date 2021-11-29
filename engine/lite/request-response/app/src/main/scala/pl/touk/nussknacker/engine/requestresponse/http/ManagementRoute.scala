package pl.touk.nussknacker.engine.requestresponse.http

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, PathMatcher1, Route}
import cats.data.NonEmptyList
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.requestresponse.api.RequestResponseDeploymentData
import pl.touk.nussknacker.engine.requestresponse.deployment.{DeploymentError, DeploymentService}

import scala.concurrent.ExecutionContext

class ManagementRoute(deploymentService: DeploymentService)
  extends Directives with FailFastCirceSupport with LazyLogging {

  def ProcessNameSegment: PathMatcher1[ProcessName] = Segment.map(ProcessName(_))

  def route(implicit ec: ExecutionContext): Route =
    path("deploy") {
      post {
        entity(as[RequestResponseDeploymentData]) { data =>
          complete {
            toResponse(deploymentService.deploy(data))
          }
        }
      }
    } ~ path("checkStatus" / ProcessNameSegment) { processName =>
      get {
        complete {
          deploymentService.checkStatus(processName) match {
            case None => HttpResponse(status = StatusCodes.NotFound)
            case Some(resp) => resp
          }
        }
      }
    } ~ path("cancel" / ProcessNameSegment) { processName =>
      post {
        complete {
          deploymentService.cancel(processName) match {
            case None => HttpResponse(status = StatusCodes.NotFound)
            case Some(resp) => resp
          }
        }
      }
    } ~ path("healthCheck") {
      get {
        complete {
          HttpResponse(status = StatusCodes.OK)
        }
      }
    }


  def toResponse(either: Either[NonEmptyList[DeploymentError], Unit]): ToResponseMarshallable = either match {
    case Right(unit) =>
      unit
    case Left(error) =>
      //TODO: something better?
      HttpResponse(status = StatusCodes.BadRequest, entity = error.toList.mkString(", "))
  }


}
