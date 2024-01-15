package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.ui.definition.DefinitionsService
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.NuPathMatchers

class DefinitionResources(
    serviceProvider: ProcessingTypeDataProvider[DefinitionsService, _],
) extends Directives
    with FailFastCirceSupport
    with NuPathMatchers
    with RouteWithUser {

  def securedRoute(implicit user: LoggedUser): Route = encodeResponse {
    pathPrefix("processDefinitionData" / Segment) { processingType =>
      serviceProvider
        .forType(processingType)
        .map { service =>
          pathEndOrSingleSlash {
            get {
              parameter(Symbol("isFragment").as[Boolean]) { isFragment =>
                complete(service.prepareUIDefinitions(processingType, isFragment))
              }
            }
          }
        }
        .getOrElse {
          complete(HttpResponse(status = StatusCodes.NotFound, entity = s"Scenario type: $processingType not found"))
        }
    }
  }

}
