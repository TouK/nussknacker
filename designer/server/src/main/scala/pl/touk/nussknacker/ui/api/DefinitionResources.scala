package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directive, Directive1, Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.ui.definition.DefinitionsService
import pl.touk.nussknacker.ui.definition.DefinitionsService.ModelParametersMode
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
              (isFragmentParam & modelParametersModeParam) { (isFragment, modelParametersMode) =>
                complete(definitionsService.prepareUIDefinitions(processingType, isFragment, modelParametersMode))
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

  private val modelParametersModeParam: Directive1[ModelParametersMode] = {
    parameter("modelParametersMode".as[String].optional).flatMap {
      case Some("ENRICHED") | None => provide(ModelParametersMode.Enriched)
      case Some("RAW")             => provide(ModelParametersMode.Raw)
      case Some(other) =>
        complete(
          HttpResponse(
            status = StatusCodes.BadRequest,
            entity = s"Unknown modelParametersMode: $other. Supported ones: ENRICHED, RAW"
          )
        )
    }
  }

}
