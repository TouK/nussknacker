package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.ExceptionHandler
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Encoder
import pl.touk.nussknacker.ui.validation.FatalValidationError
import pl.touk.nussknacker.ui._

import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal


object EspErrorToHttp extends LazyLogging with FailFastCirceSupport {

  def espErrorToHttp(error: ResponseError): HttpResponse = HttpResponse(status = error.statusCode.getOrElse(StatusCodes.InternalServerError), entity = error.getMessage)

  def errorToHttp : PartialFunction[Throwable, HttpResponse] = {
    case e: ResponseError => espErrorToHttp(e)
    case ex: IllegalArgumentException =>
      logger.debug(s"Illegal argument: ${ex.getMessage}", ex)
      HttpResponse(status = StatusCodes.BadRequest, entity = ex.getMessage)
    case ex =>
      logger.error(s"Unknown error: ${ex.getMessage}", ex)
      HttpResponse(status = StatusCodes.InternalServerError, entity = ex.getMessage)
  }

  def espErrorHandler: ExceptionHandler = {
    import akka.http.scaladsl.server.Directives._
    ExceptionHandler {
      case NonFatal(e) => complete(errorToHttp(e))
    }
  }

  def toResponseEither[T: Encoder](either: Either[ResponseError, T]): ToResponseMarshallable = either match {
    case Right(t) => t
    case Left(err) => espErrorToHttp(err)
  }

  def toResponseEither[T: Encoder](either: Either[ResponseError, T], okStatus: StatusCode): HttpResponse = {
    import io.circe.syntax._

    either match {
      case Right(t) =>
        HttpResponse(status = okStatus, entity = HttpEntity(ContentTypes.`application/json`, t.asJson.noSpaces))
      case Left(err) =>
        espErrorToHttp(err)
    }
  }

  def toResponse(okStatus: StatusCode)(xor: Either[ResponseError, Unit]): HttpResponse = xor match {
    case Left(error) => espErrorToHttp(error)
    case Right(_) => HttpResponse(status = okStatus)
  }

  def toResponseTryPF(okStatus: StatusCode): PartialFunction[Try[Unit], HttpResponse] = {
    case Failure(ex) => errorToHttp(ex)
    case Success(_) => HttpResponse(status = okStatus)
  }

}

