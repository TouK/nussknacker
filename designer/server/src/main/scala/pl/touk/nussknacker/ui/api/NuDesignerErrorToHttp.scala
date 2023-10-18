package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.ExceptionHandler
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Encoder
import pl.touk.nussknacker.ui._

import scala.language.implicitConversions
import scala.util.control.NonFatal

object NuDesignerErrorToHttp extends LazyLogging with FailFastCirceSupport {

  def nuDesignerErrorHandler: ExceptionHandler = {
    import akka.http.scaladsl.server.Directives._
    ExceptionHandler { case NonFatal(e) =>
      complete(errorToHttp(e))
    }
  }

  private def errorToHttp: PartialFunction[Throwable, HttpResponse] = {
    case e: NuDesignerError => nuDesignerErrorToHttp(e)
    case ex: IllegalArgumentException =>
      logger.debug(s"Illegal argument: ${ex.getMessage}", ex)
      HttpResponse(status = StatusCodes.BadRequest, entity = ex.getMessage)
    case ex =>
      logger.error(s"Unknown error: ${ex.getMessage}", ex)
      HttpResponse(status = StatusCodes.InternalServerError, entity = ex.getMessage)
  }

  def toResponseEither[T: Encoder](either: Either[NuDesignerError, T]): ToResponseMarshallable = either match {
    case Right(t)  => t
    case Left(err) => nuDesignerErrorToHttp(err)
  }

  def nuDesignerErrorToHttp(error: NuDesignerError): HttpResponse = {
    val statusCode = error match {
      case _: NotFoundError         => StatusCodes.NotFound
      case _: FatalError            => StatusCodes.InternalServerError
      case _: BadRequestError       => StatusCodes.BadRequest
      case _: IllegalOperationError => StatusCodes.Conflict
      // unknown?
      case _ =>
        logger.error(s"Unknown NuDesignerError: ${error.getMessage}. Http status 500 will be returned.", error)
        StatusCodes.InternalServerError
    }
    HttpResponse(status = statusCode, entity = error.getMessage)
  }

}
