package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.ui.definition.{AdditionalUIConfigFinalizer, ModelDefinitionEnricher, UIProcessObjectsFactory}
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.process.fragment.FragmentRepository
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.NuPathMatchers

import scala.concurrent.ExecutionContext

class DefinitionResources(
    processingTypeDataProvider: ProcessingTypeDataProvider[
      (ProcessingTypeData, ModelDefinitionEnricher, AdditionalUIConfigFinalizer),
      _
    ],
    fragmentRepository: FragmentRepository,
    getProcessCategoryService: () => ProcessCategoryService,
)(implicit ec: ExecutionContext)
    extends Directives
    with FailFastCirceSupport
    with NuPathMatchers
    with RouteWithUser {

  def securedRoute(implicit user: LoggedUser): Route = encodeResponse {
    pathPrefix("processDefinitionData" / Segment) { processingType =>
      processingTypeDataProvider
        .forType(processingType)
        .map { case (processingTypeData, modelDefinitionEnricher, additionalUIConfigFinalizer) =>
          pathEndOrSingleSlash {
            get {
              parameter(Symbol("isFragment").as[Boolean]) { isFragment =>
                complete(
                  fragmentRepository.fetchLatestFragments(processingType).map { fragments =>
                    val enrichedModelDefinition =
                      modelDefinitionEnricher
                        .modelDefinitionWithBuiltInComponentsAndFragments(isFragment, fragments, processingType)
                    val finalizedScenarioPropertiesConfig = additionalUIConfigFinalizer
                      .finalizeScenarioProperties(processingTypeData.scenarioPropertiesConfig, processingType)
                    UIProcessObjectsFactory.prepareUIProcessObjects(
                      enrichedModelDefinition,
                      processingTypeData.modelData,
                      processingTypeData.deploymentManager,
                      user,
                      isFragment,
                      getProcessCategoryService(),
                      finalizedScenarioPropertiesConfig,
                      processingType
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
