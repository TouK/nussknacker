package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.engine.api.dict.{DictDefinition, DictQueryService}
import pl.touk.nussknacker.ui.definition.DefinitionsService
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.NuPathMatchers

import scala.concurrent.ExecutionContext

class DefinitionResources(
    definitionsServices: ProcessingTypeDataProvider[
      (DefinitionsService, DictQueryService, Map[String, DictDefinition]),
      _
    ],
)(implicit val ec: ExecutionContext)
    extends Directives
    with FailFastCirceSupport
    with NuPathMatchers
    with RouteWithUser {

  private val dictResources = new DictResources

  def securedRoute(implicit user: LoggedUser): Route = encodeResponse {
    pathPrefix("processDefinitionData" / Segment) { processingType =>
      definitionsServices
        .forType(processingType)
        .map { case (definitionsService, dictQueryService, dictionaries) =>
          pathEndOrSingleSlash {
            get {
              parameter(Symbol("isFragment").as[Boolean]) { isFragment =>
                complete(definitionsService.prepareUIDefinitions(processingType, isFragment))
              }
            }
          } ~ dictResources.route(dictQueryService, dictionaries)
        }
        .getOrElse {
          complete(HttpResponse(status = StatusCodes.NotFound, entity = s"Scenario type: $processingType not found"))
        }
    }
  }

}
