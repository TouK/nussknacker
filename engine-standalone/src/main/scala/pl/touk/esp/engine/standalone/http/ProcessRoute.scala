package pl.touk.esp.engine.standalone.http


import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.esp.engine.api.exception.EspExceptionInfo
import pl.touk.esp.engine.standalone.management.DeploymentService
import pl.touk.esp.engine.util.ThreadUtils

import scala.concurrent.ExecutionContext

class ProcessRoute(processesClassLoader: ClassLoader, deploymentService: DeploymentService)  extends Directives with Argonaut62Support with LazyLogging {

  import argonaut.ArgonautShapeless._

  def route(implicit ec: ExecutionContext): Route = ThreadUtils.withThisAsContextClassLoader(processesClassLoader) {
    path(Segment) { processId =>
      post {
        entity(as[Array[Byte]]) { bytes =>
          val interpreter = deploymentService.getInterpreter(processId)
          interpreter match {
            case None =>
              complete {
                HttpResponse(status = StatusCodes.NotFound)
              }
            case Some(processInterpreter) =>
              val input = processInterpreter.source.toObject(bytes)
              complete {
                //TODO: wiele wynikow??
                processInterpreter.invoke(input).map(toResponse)
              }
          }
        }
      }
    }
  }

  def toResponse(either: Either[Option[Any], EspExceptionInfo[_ <: Throwable]]) : ToResponseMarshallable = either match {
    case Right(exception) => EspError(exception.nodeId, exception.throwable.getMessage): ToResponseMarshallable
    case Left(Some(response)) => response.toString
    case Left(None) => ""
  }

  case class EspError(nodeId: Option[String], message: String)


}
