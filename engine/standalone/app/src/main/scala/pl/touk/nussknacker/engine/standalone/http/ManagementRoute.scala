package pl.touk.nussknacker.engine.standalone.http

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, PathMatcher1, Route}
import argonaut.CodecJson
import cats.data.NonEmptyList
import com.typesafe.scalalogging.LazyLogging
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.nussknacker.engine.api.deployment.RunningState
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.standalone.api.DeploymentData
import pl.touk.nussknacker.engine.standalone.deployment.{DeploymentError, DeploymentService}
import pl.touk.nussknacker.engine.util.json.Codecs

import scala.concurrent.ExecutionContext

class ManagementRoute(deploymentService: DeploymentService) extends Directives with Argonaut62Support with LazyLogging {

  import argonaut.ArgonautShapeless._
  private implicit val stateCodec: CodecJson[RunningState.Value] = Codecs.enumCodec(RunningState)

  def ProcessNameSegment: PathMatcher1[ProcessName] = Segment.map(ProcessName)

  def route(implicit ec: ExecutionContext): Route =
    path("deploy") {
      post {
        entity(as[DeploymentData]) { data =>
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
    }


  def toResponse(either: Either[NonEmptyList[DeploymentError], Unit]): ToResponseMarshallable = either match {
    case Right(unit) =>
      unit
    case Left(error) =>
      //TODO: something better?
      HttpResponse(status = StatusCodes.BadRequest, entity = error.toList.mkString(", "))
  }


}
