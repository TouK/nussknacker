package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.ui.validation.{FatalValidationError, ProcessValidation}
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolving

import scala.concurrent.ExecutionContext

class ValidationResources(processResolving: UIProcessResolving)
                         (implicit ec: ExecutionContext)
  extends Directives with FailFastCirceSupport with RouteWithUser {

  def securedRoute(implicit user: LoggedUser): Route =
    path("processValidation") {
      post {
        entity(as[DisplayableProcess]) { displayable =>
          complete {
            EspErrorToHttp.toResponseEither(FatalValidationError.renderNotAllowedAsError(processResolving.validateBeforeUiResolving(displayable)))
          }
        }
      }
    }

}

