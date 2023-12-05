package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.engine.api.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver
import pl.touk.nussknacker.ui.validation.FatalValidationError

import scala.concurrent.{ExecutionContext, Future}

class ValidationResources(
    protected val processService: ProcessService,
    processResolver: UIProcessResolver
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

  private def validate(displayable: DisplayableProcess)(implicit user: LoggedUser) = {
    NuDesignerErrorToHttp.toResponseEither(
      FatalValidationError.renderNotAllowedAsError(
        processResolver.validateBeforeUiResolving(displayable)
      )
    )
  }

}
