package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCode, StatusCodes}
import akka.http.scaladsl.server.ExceptionHandler
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Encoder
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.ui.process.deployment.ProcessIsBeingDeployed
import pl.touk.nussknacker.ui.{BadRequestError, EspError, FatalError, NotFoundError}
import pl.touk.nussknacker.ui.validation.FatalValidationError

import scala.language.implicitConversions
import scala.util.control.NonFatal
import scala.util.parsing.json.JSONObject


object EspErrorToHttp extends LazyLogging with FailFastCirceSupport {

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
      case NonFatal(e) => complete(errorToHttp(e))
    }
  }

  def toResponseXor[T: Encoder](xor: Either[EspError, T]): ToResponseMarshallable =
    xor match {
      case Right(t) =>
        t
      case Left(err) =>
        espErrorToHttp(err)
    }

  def toResponseEither[T: Encoder](either: Either[EspError, T]): ToResponseMarshallable = either match {
      case Right(t) => t
      case Left(err) => espErrorToHttp(err)
  }


  def toResponse(okStatus: StatusCode)(xor: Either[EspError, Unit]): HttpResponse = xor match {
    case Left(error) => espErrorToHttp(error)
    case Right(_) => HttpResponse(status = okStatus)
  }

  def toResponseReject(message: String): ToResponseMarshallable = {
    val entity = Encoder.encodeMap[String, String].apply(Map("message" -> message)).spaces2
    HttpResponse(status = StatusCodes.BadRequest, entity = HttpEntity(ContentTypes.`application/json`, entity))
  }
}

