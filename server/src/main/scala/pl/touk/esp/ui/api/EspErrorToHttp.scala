package pl.touk.esp.ui.api

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.esp.ui.process.deployment.ProcessIsBeingDeployed
import pl.touk.esp.ui.{BadRequestError, EspError, FatalError, NotFoundError}

import scala.language.implicitConversions

object EspErrorToHttp extends LazyLogging {

  def espErrorToHttp(error: EspError) : HttpResponse = {
    val statusCode = error match {
      case e: NotFoundError => StatusCodes.NotFound
      case e: FatalError => StatusCodes.InternalServerError
      case e: BadRequestError => StatusCodes.BadRequest
      case e: ProcessIsBeingDeployed => StatusCodes.Conflict
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

}
