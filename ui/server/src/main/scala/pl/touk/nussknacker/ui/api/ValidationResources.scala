package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.ui.validation.{FatalValidationError, ProcessValidation}
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext

class ValidationResources(processValidation: ProcessValidation)
                         (implicit ec: ExecutionContext)
  extends Directives with FailFastCirceSupport with RouteWithUser {

  import pl.touk.nussknacker.restmodel.CirceRestCodecs.displayableDecoder

  def route(implicit user: LoggedUser): Route =
    path("processValidation") {
      post {
        entity(as[DisplayableProcess]) { displayable =>
          complete {
            EspErrorToHttp.toResponseEither(FatalValidationError.renderNotAllowedAsError(processValidation.validate(displayable)))
          }
        }
      }
    }

}

