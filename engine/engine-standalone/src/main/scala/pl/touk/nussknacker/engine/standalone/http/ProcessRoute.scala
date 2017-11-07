package pl.touk.nussknacker.engine.standalone.http

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import argonaut.Argonaut._
import argonaut.ArgonautShapeless._
import com.typesafe.scalalogging.LazyLogging
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.nussknacker.engine.standalone.management.DeploymentService

import scala.concurrent.ExecutionContext

class ProcessRoute(deploymentService: DeploymentService) extends Directives with LazyLogging with Argonaut62Support {

  def route(implicit ec: ExecutionContext): Route =
    path(Segment) { processPath =>

      deploymentService.getInterpreterByPath(processPath) match {
        case None =>
          complete {
            HttpResponse(status = StatusCodes.NotFound)
          }
        case Some(processInterpreter) => processInterpreter.invoke {
          case Left(errors) => complete {
            (StatusCodes.InternalServerError, errors.toList.map(info => EspError(info.nodeId, info.throwable.getMessage)).asJson)
          }
          case Right(results) => complete {
            (StatusCodes.OK, results)
          }
        }
      }
    }


  case class EspError(nodeId: Option[String], message: String)

}
