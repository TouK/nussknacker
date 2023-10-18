package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolving
import pl.touk.nussknacker.ui.validation.FatalValidationError

import scala.concurrent.{ExecutionContext, Future}

class ValidationResources(
    protected val processService: ProcessService,
    processResolving: UIProcessResolving
)(implicit val ec: ExecutionContext)
    extends Directives
    with FailFastCirceSupport
    with RouteWithUser
    with ProcessDirectives {

  def securedRoute(implicit user: LoggedUser): Route =
    path("processValidation") {
      post {
        entity(as[DisplayableProcess]) { displayable =>
          complete {
            validate(displayable)
          }
        }
      }
    }

  private def validate(displayable: DisplayableProcess) = {
    EspErrorToHttp.toResponseEither(
      FatalValidationError.renderNotAllowedAsError(
        processResolving.validateBeforeUiResolving(displayable)
      )
    )
  }

}
