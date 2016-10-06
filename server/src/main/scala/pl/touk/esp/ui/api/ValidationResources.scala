package pl.touk.esp.ui.api

import akka.http.scaladsl.server.Directives
import argonaut.{EncodeJson, Json, PrettyParams}
import pl.touk.esp.ui.api.ProcessValidation.ValidationResult
import pl.touk.esp.ui.process.displayedgraph.DisplayableProcess
import pl.touk.esp.ui.process.marshall.{DisplayableProcessCodec, ProcessConverter}
import pl.touk.esp.ui.security.LoggedUser
import pl.touk.esp.ui.util.Argonaut62Support

import scala.concurrent.ExecutionContext

class ValidationResources(processValidation: ProcessValidation, processConverter: ProcessConverter)
                         (implicit ec: ExecutionContext)
  extends Directives with Argonaut62Support{

  import argonaut.ArgonautShapeless._

  implicit val displayableProcessDecode = DisplayableProcessCodec.codec

  implicit val printer: Json => String =
    PrettyParams.spaces2.copy(dropNullKeys = true, preserveOrder = true).pretty

  implicit val validationResultEncode = EncodeJson.of[ValidationResult]

  val route = (user:LoggedUser) =>
    path("processValidation") {
      post {
        entity(as[DisplayableProcess]) { displayable =>
          complete {
            val canonical = processConverter.fromDisplayable(displayable)
            processValidation.validate(canonical)
          }
        }
      }
    }

}
