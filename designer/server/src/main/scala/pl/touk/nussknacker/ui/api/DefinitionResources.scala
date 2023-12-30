package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.engine.api.component.AdditionalUIConfigProvider
import pl.touk.nussknacker.engine.{ModelData, ProcessingTypeData}
import pl.touk.nussknacker.ui.definition.UIProcessObjectsFactory
import pl.touk.nussknacker.ui.process.fragment.FragmentRepository
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.{ComponentNamesFinder, ProcessCategoryService}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.NuPathMatchers

import scala.concurrent.ExecutionContext

class DefinitionResources(
    modelDataProvider: ProcessingTypeDataProvider[ModelData, _],
    processingTypeDataProvider: ProcessingTypeDataProvider[ProcessingTypeData, _],
    fragmentRepository: FragmentRepository,
    getProcessCategoryService: () => ProcessCategoryService,
    additionalUIConfigProvider: AdditionalUIConfigProvider
)(implicit ec: ExecutionContext)
    extends Directives
    with FailFastCirceSupport
    with NuPathMatchers
    with RouteWithUser {

  def securedRoute(implicit user: LoggedUser): Route = encodeResponse {
    pathPrefix("processDefinitionData" / Segment) { processingType =>
      processingTypeDataProvider
        .forType(processingType)
        .map { processingTypeData =>
          // TODO maybe always return data for all fragments versions instead of fetching just one-by-one?
          pathEndOrSingleSlash {
            get {
              parameter(Symbol("isFragment").as[Boolean]) { isFragment =>
                complete(
                  fragmentRepository.fetchLatestFragments(processingType).map { fragments =>
                    UIProcessObjectsFactory.prepareUIProcessObjects(
                      processingTypeData.modelData,
                      processingTypeData.staticModelDefinition,
                      processingTypeData.deploymentManager,
                      user,
                      fragments,
                      isFragment,
                      getProcessCategoryService(),
                      processingTypeData.scenarioPropertiesConfig,
                      processingType,
                      additionalUIConfigProvider
                    )
                  }
                )
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
