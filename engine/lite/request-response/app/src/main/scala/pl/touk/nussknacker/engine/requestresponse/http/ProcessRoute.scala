package pl.touk.nussknacker.engine.requestresponse.http

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.Materializer
import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.JsonCodec
import io.circe.syntax._
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.requestresponse.deployment.ProcessInterpreters
import pl.touk.nussknacker.engine.requestresponse.http.logging.RequestResponseLogger

import scala.concurrent.ExecutionContext

class ProcessRoute(processInterpreters: ProcessInterpreters) extends Directives with LazyLogging with FailFastCirceSupport {

  def route(log: RequestResponseLogger)
           (implicit ec: ExecutionContext, mat: Materializer): Route =
    path(Segment) { processPath =>
      log.loggingDirective(processPath)(mat) {
        processInterpreters.getInterpreterByPath(processPath) match {
          case None =>
            complete {
              HttpResponse(status = StatusCodes.NotFound)
            }
          case Some(processInterpreter) => new RequestResponseRequestHandler(processInterpreter).invoke {
            case Invalid(errors) => complete {
              logErrors(processPath, errors)
              (StatusCodes.InternalServerError, errors.toList.map(info => EspError(info.nodeId, Option(info.throwable.getMessage))).asJson)
            }
            case Valid(results) => complete {
              (StatusCodes.OK, results)
            }
          }
        }
      }
      //TODO place openApi endpoint
    } ~ pathEndOrSingleSlash {
      //healthcheck endpoint
      get {
        complete {
          HttpResponse(status = StatusCodes.OK)
        }
      }
    }


  private def logErrors(processPath: String, errors: NonEmptyList[NuExceptionInfo[_ <: Throwable]]): Unit = {
    logger.warn(s"Failed to invoke: $processPath with errors: ${errors.map(_.throwable.getMessage)}")
    errors.toList.foreach { error =>
      logger.info(s"Invocation failed $processPath, error in ${error.nodeId}: ${error.throwable.getMessage}", error.throwable)
    }
  }

  @JsonCodec case class EspError(nodeId: Option[String], message: Option[String])

}
