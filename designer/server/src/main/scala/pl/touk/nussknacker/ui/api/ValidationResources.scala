package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.ui.api.utils.ScenarioDetailsOps._
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolver
import pl.touk.nussknacker.ui.validation.FatalValidationError

import scala.concurrent.ExecutionContext

class ValidationResources(
    protected val processService: ProcessService,
    processResolver: ProcessingTypeDataProvider[UIProcessResolver, _]
)(implicit val ec: ExecutionContext)
    extends Directives
    with FailFastCirceSupport
    with RouteWithUser
    with ProcessDirectives {

  def securedRoute(implicit user: LoggedUser): Route =
    path("processValidation" / ProcessNameSegment) { processName =>
      (post & processDetailsForName(processName)) { details: ScenarioWithDetails =>
        entity(as[ScenarioValidationRequest]) { request =>
          complete {
            NuDesignerErrorToHttp.toResponseEither(
              FatalValidationError.renderNotAllowedAsError(
                processResolver
                  .forProcessingTypeUnsafe(details.processingType)
                  .validateBeforeUiResolving(
                    request.scenarioGraph,
                    request.processName,
                    details.isFragment,
                    details.scenarioLabels
                  )
              )
            )
          }
        }
      }
    }

}

@JsonCodec case class ScenarioValidationRequest(processName: ProcessName, scenarioGraph: ScenarioGraph)
