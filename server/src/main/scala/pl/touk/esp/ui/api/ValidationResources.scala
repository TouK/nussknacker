package pl.touk.esp.ui.api

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, ResponseEntity, StatusCodes}
import akka.http.scaladsl.server.Directives
import argonaut.{EncodeJson, Json, PrettyParams}
import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import pl.touk.esp.engine.compile.{ProcessCompilationError, ProcessValidator}
import pl.touk.esp.engine.util.ReflectUtils
import pl.touk.esp.ui.process.displayedgraph.DisplayableProcess
import pl.touk.esp.ui.process.marshall.{DisplayableProcessCodec, ProcessConverter}
import pl.touk.esp.ui.util.Argonaut62Support

import scala.concurrent.{ExecutionContext, Future}

class ValidationResources(processValidator: ProcessValidator)
                         (implicit ec: ExecutionContext)
  extends Directives with Argonaut62Support {

  import pl.touk.esp.ui.util.CollectionsEnrichments._
  import argonaut.ArgonautShapeless._

  implicit val displayableProcessDecode = DisplayableProcessCodec.decoder

  implicit val printer: Json => String =
    PrettyParams.spaces2.copy(dropNullKeys = true, preserveOrder = true).pretty

  implicit val errorEncode = EncodeJson.of[Map[String, List[String]]]

  val route =
    path("processValidation") {
      post {
        entity(as[DisplayableProcess]) { displayable =>
          complete {
            val canonical = ProcessConverter.fromDisplayable(displayable)
            processValidator.validate(canonical) match {
              case Valid(_) =>
                Future.successful(HttpResponse(StatusCodes.OK))
              case Invalid(errors) =>
                Marshal(formatErrors(errors)).to[ResponseEntity].map { entity =>
                  HttpResponse(StatusCodes.BadRequest, entity = entity)
                }
            }
          }
        }
      }
    }

  private def formatErrors(errors: NonEmptyList[ProcessCompilationError]): Map[String, List[String]] = {
    (for {
      error <- errors.toList
      nodeId <- error.nodeIds
    } yield nodeId -> formatErrorMessage(error)).flatGroupByKey
  }

  private def formatErrorMessage(error: ProcessCompilationError): String = {
    ReflectUtils.fixedClassSimpleNameWithoutParentModule(error.getClass) // TODO: better messages
  }

}
