package pl.touk.esp.ui.api

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpResponse, ResponseEntity, StatusCodes}
import akka.http.scaladsl.server.Directives
import argonaut.{EncodeJson, Json, PrettyParams}
import cats.data.Validated.{Invalid, Valid}
import pl.touk.esp.engine.compile.ProcessValidator
import pl.touk.esp.ui.api.ProcessValidation.ValidationResult
import pl.touk.esp.ui.process.displayedgraph.DisplayableProcess
import pl.touk.esp.ui.process.marshall.{DisplayableProcessCodec, ProcessConverter}
import pl.touk.esp.ui.util.Argonaut62Support

import scala.concurrent.{ExecutionContext, Future}

class ValidationResources(processValidation: ProcessValidation, processConverter: ProcessConverter)
                         (implicit ec: ExecutionContext)
  extends Directives with Argonaut62Support{

  import argonaut.ArgonautShapeless._

  implicit val displayableProcessDecode = DisplayableProcessCodec.codec

  implicit val printer: Json => String =
    PrettyParams.spaces2.copy(dropNullKeys = true, preserveOrder = true).pretty

  implicit val validationResultEncode = EncodeJson.of[ValidationResult]

  val route =
    path("processValidation") {
      post {
        entity(as[DisplayableProcess]) { displayable =>
          complete {
            val canonical = processConverter.fromDisplayable(displayable)
            processValidation.validate(canonical) match {
              case Valid(_) =>
                Future.successful(HttpResponse(StatusCodes.OK))
              case Invalid(validationResult) =>
                Marshal(validationResult).to[ResponseEntity].map { entity =>
                  HttpResponse(StatusCodes.BadRequest, entity = entity)
                }
            }
          }
        }
      }
    }

}
