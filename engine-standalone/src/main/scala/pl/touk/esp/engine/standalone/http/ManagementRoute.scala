package pl.touk.esp.engine.standalone.http

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import cats.data.NonEmptyList
import com.typesafe.scalalogging.LazyLogging
import pl.touk.esp.engine.api.deployment.DeploymentData
import pl.touk.esp.engine.standalone.management.{DeploymentError, DeploymentService}
import pl.touk.esp.engine.util.ThreadUtils
import pl.touk.http.argonaut.Argonaut62Support

import scala.concurrent.ExecutionContext

class ManagementRoute(processesClassLoader: ClassLoader, deploymentService: DeploymentService) extends Directives with Argonaut62Support with LazyLogging  {

  import argonaut.ArgonautShapeless._

  def route(implicit ec: ExecutionContext): Route = ThreadUtils.withThisAsContextClassLoader(processesClassLoader) {
    path("deploy") {
      post {
        entity(as[DeploymentData]) { data =>
          complete {
            toResponse(deploymentService.deploy(data.processId, data.processJson))
          }
        }
      }
    } ~ path("checkStatus" / Segment) { processId =>
      get {
        complete {
          deploymentService.checkStatus(processId) match {
            case None => HttpResponse(status = StatusCodes.NotFound)
            case Some(resp) => resp
          }
        }
      }
    } ~ path("cancel" / Segment) { processId =>
      post {
        complete {
          deploymentService.cancel(processId) match {
            case None => HttpResponse(status = StatusCodes.NotFound)
            case Some(resp) => resp
          }
        }
      }
    }
  }


  def toResponse(either: Either[NonEmptyList[DeploymentError], Unit]): ToResponseMarshallable = either match {
    case Right(unit) =>
      unit
    case Left(error) =>
      //TODDO: cos lepszego?
      HttpResponse(status = StatusCodes.BadRequest, entity = error.toList.mkString(", "))
  }


}
