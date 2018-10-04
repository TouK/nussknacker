package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, StatusCode, StatusCodes}
import akka.http.scaladsl.server.ExceptionHandler
import argonaut.EncodeJson
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.process.deployment.ProcessIsBeingDeployed
import pl.touk.nussknacker.ui.validation.ValidationResults.{FatalValidationError, ValidationResult}
import pl.touk.nussknacker.ui.{BadRequestError, EspError, FatalError, NotFoundError}
import pl.touk.http.argonaut.Argonaut62Support

import scala.language.implicitConversions

object EspErrorToHttp extends LazyLogging with Argonaut62Support {

  import pl.touk.nussknacker.ui.codec.UiCodecs.validationResultEncode

  def espErrorToHttp(error: EspError) : HttpResponse = {
    val statusCode = error match {
      case e: NotFoundError => StatusCodes.NotFound
      case e: FatalError => StatusCodes.InternalServerError
      case e: BadRequestError => StatusCodes.BadRequest
      case e: ProcessIsBeingDeployed => StatusCodes.Conflict
      case e: FatalValidationError => StatusCodes.BadRequest
      //unknown?
      case _ =>
        logger.error(s"Unknown EspError: ${error.getMessage}", error)
        StatusCodes.InternalServerError
    }
    HttpResponse(status = statusCode, entity = error.getMessage)
  }

  def errorToHttp : PartialFunction[Throwable, HttpResponse] = {
    case e:EspError => espErrorToHttp(e)
    case ex =>
      logger.error(s"Unknown error: ${ex.getMessage}", ex)
      HttpResponse(status = StatusCodes.InternalServerError, entity = ex.getMessage)
  }

  def espErrorHandler: ExceptionHandler = {
    import akka.http.scaladsl.server.Directives._
    ExceptionHandler {
      case e: EspError => complete(errorToHttp(e))
    }
  }

  def toResponse(either: Either[EspError, ValidationResult]): ToResponseMarshallable =
    either match {
      case Right(validationResult) =>
        validationResult
      case Left(err) =>
        espErrorToHttp(err)
    }

  def toResponseXor[T: EncodeJson](xor: Either[EspError, T]): ToResponseMarshallable =
    xor match {
      case Right(t) =>
        t
      case Left(err) =>
        espErrorToHttp(err)
    }

  def toResponseEither[T: EncodeJson](either: Either[EspError, T]): ToResponseMarshallable = either match {
      case Right(t) => t
      case Left(err) => espErrorToHttp(err)
  }


  def toResponse(okStatus: StatusCode)(xor: Either[EspError, Unit]): HttpResponse = xor match {
    case Left(error) => espErrorToHttp(error)
    case Right(_) => HttpResponse(status = okStatus)
  }


}
