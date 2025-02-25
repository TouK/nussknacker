package pl.touk.nussknacker.ui.api

import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.server.{Directive1, Directives, Route}
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.ui.definition.DefinitionsService
import pl.touk.nussknacker.ui.definition.DefinitionsService.ComponentUiConfigMode
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.NuPathMatchers

import scala.concurrent.ExecutionContext

class DefinitionResources(
    definitionsServices: ProcessingTypeDataProvider[DefinitionsService, _]
)(implicit val ec: ExecutionContext)
    extends Directives
    with FailFastCirceSupport
    with NuPathMatchers
    with RouteWithUser {

  def securedRoute(implicit user: LoggedUser): Route = encodeResponse {
    pathPrefix("processDefinitionData" / Segment) { processingType =>
      definitionsServices
        .forProcessingType(processingType)
        .map { case (definitionsService) =>
          pathEndOrSingleSlash {
            get {
              (isFragmentParam & componentUiConfigModeParam) { (isFragment, componentUiConfigMode) =>
                complete(definitionsService.prepareUIDefinitions(processingType, isFragment, componentUiConfigMode))
              }
            }
          }
        }
        .getOrElse {
          complete(HttpResponse(status = StatusCodes.NotFound, entity = s"Scenario type: $processingType not found"))
        }
    }
  }

  private val isFragmentParam: Directive1[Boolean] = parameter(Symbol("isFragment").as[Boolean])

  private val componentUiConfigModeParam: Directive1[ComponentUiConfigMode] = {
    // parameter used only by an external project to fetch component definitions without enrichments
    parameter("enrichedWithUiConfig".as[Boolean].optional).flatMap {
      case Some(true) | None => provide(ComponentUiConfigMode.EnrichedWithUiConfig)
      case Some(false)       => provide(ComponentUiConfigMode.BasicConfig)
    }
  }

}
