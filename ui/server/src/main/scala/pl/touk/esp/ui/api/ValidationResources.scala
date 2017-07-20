package pl.touk.esp.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import pl.touk.esp.ui.validation.ProcessValidation
import pl.touk.esp.ui.process.displayedgraph.DisplayableProcess
import pl.touk.esp.ui.process.marshall.ProcessConverter
import pl.touk.esp.ui.security.LoggedUser
import pl.touk.http.argonaut.Argonaut62Support

import scala.concurrent.ExecutionContext

class ValidationResources(processValidation: ProcessValidation)
                         (implicit ec: ExecutionContext)
  extends Directives with Argonaut62Support  with RouteWithUser {

  import argonaut.ArgonautShapeless._
  import pl.touk.esp.ui.codec.UiCodecs._

  def route(implicit user: LoggedUser): Route =
    path("processValidation") {
      post {
        entity(as[DisplayableProcess]) { displayable =>
          complete {
            EspErrorToHttp.toResponse(processValidation.validate(displayable).fatalAsError)
          }
        }
      }
    }

}

