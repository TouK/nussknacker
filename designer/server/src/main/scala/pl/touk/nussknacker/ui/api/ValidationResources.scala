package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.process.ProcessIdWithNameAndCategory
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolving
import pl.touk.nussknacker.ui.validation.FatalValidationError

import scala.concurrent.{ExecutionContext, Future}

class ValidationResources(val processRepository: FetchingProcessRepository[Future], processResolving: UIProcessResolving)(implicit val ec: ExecutionContext)
  extends Directives with FailFastCirceSupport with RouteWithUser with ProcessDirectives {

  def securedRoute(implicit user: LoggedUser): Route =
    path("processValidation") {
      post {
        entity(as[DisplayableProcess]) { displayable =>
          displayable.category.map { categoryFromRequest =>
            complete {
              validate(displayable, categoryFromRequest)
            }
          }.getOrElse(
            processIdWithCategory(displayable.id) { case ProcessIdWithNameAndCategory(_, _, categoryFromDb) => //todo: to be removed after adoption of version with category in DisplayableProcess
              complete {
                validate(displayable, categoryFromDb)
              }
            }
          )
        }
      }
    }

  private def validate(displayable: DisplayableProcess, category: String) = {
    EspErrorToHttp.toResponseEither(FatalValidationError.renderNotAllowedAsError(
      processResolving.validateBeforeUiResolving(displayable, category)
    ))
  }
}

