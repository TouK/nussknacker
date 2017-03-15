package pl.touk.esp.ui.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, StatusCode, StatusCodes}
import argonaut.EncodeJson
import cats.data.Xor
import com.typesafe.scalalogging.LazyLogging
import pl.touk.esp.ui.process.deployment.ProcessIsBeingDeployed
import pl.touk.esp.ui.util.Argonaut62Support
import pl.touk.esp.ui.validation.ValidationResults.{FatalValidationError, ValidationResult}
import pl.touk.esp.ui.{BadRequestError, EspError, FatalError, NotFoundError}

import scala.language.implicitConversions

object EspErrorToHttp extends LazyLogging with Argonaut62Support {

  import pl.touk.esp.ui.codec.UiCodecs.validationResultEncode

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


  def toResponse(xor: Xor[EspError, ValidationResult]): ToResponseMarshallable =
    xor.flatMap(_.fatalAsError) match {
      case Xor.Right(validationResult) =>
        validationResult
      case Xor.Left(err) =>
        espErrorToHttp(err)
    }

  def toResponseXor[T: EncodeJson](xor: Xor[EspError, T]): ToResponseMarshallable =
    xor match {
      case Xor.Right(t) =>
        t
      case Xor.Left(err) =>
        espErrorToHttp(err)
    }

  def toResponse(okStatus: StatusCode)(xor: Xor[EspError, Unit]): HttpResponse = xor match {
    case Xor.Left(error) => espErrorToHttp(error)
    case Xor.Right(_) => HttpResponse(status = okStatus)
  }


}
