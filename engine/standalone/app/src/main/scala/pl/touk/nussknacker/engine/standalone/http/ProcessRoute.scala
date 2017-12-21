package pl.touk.nussknacker.engine.standalone.http

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import argonaut.Argonaut._
import argonaut.ArgonautShapeless._
import cats.data.NonEmptyList
import com.typesafe.scalalogging.LazyLogging
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.standalone.StandaloneRequestHandler
import pl.touk.nussknacker.engine.standalone.deployment.DeploymentService

import scala.concurrent.ExecutionContext

class ProcessRoute(deploymentService: DeploymentService) extends Directives with LazyLogging with Argonaut62Support {

  def route(implicit ec: ExecutionContext): Route =
    path(Segment) { processPath =>

      deploymentService.getInterpreterByPath(processPath) match {
        case None =>
          complete {
            HttpResponse(status = StatusCodes.NotFound)
          }
        case Some(processInterpreter) => new StandaloneRequestHandler(processInterpreter).invoke {
          case Left(errors) => complete {
            logErrors(processPath, errors)
            (StatusCodes.InternalServerError, errors.toList.map(info => EspError(info.nodeId, Option(info.throwable.getMessage))).asJson)
          }
          case Right(results) => complete {
            (StatusCodes.OK, results)
          }
        }
      }
    }


  private def logErrors(processPath: JsonField, errors: NonEmptyList[EspExceptionInfo[_ <: Throwable]]) = {
    logger.warn(s"Failed to invoke: $processPath with errors: ${errors.map(_.throwable.getMessage)}")
    errors.toList.foreach { error =>
      logger.info(s"Invocation failed $processPath, error in ${error.nodeId}: ${error.throwable.getMessage}", error.throwable)
    }
  }

  case class EspError(nodeId: Option[String], message: Option[String])

}
