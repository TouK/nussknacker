package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.engine.api.component.AdditionalUIConfigProvider
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfigParser
import pl.touk.nussknacker.engine.{ModelData, ProcessingTypeData}
import pl.touk.nussknacker.ui.definition.{ModelDefinitionEnricher, UIProcessObjectsFactory}
import pl.touk.nussknacker.ui.process.fragment.FragmentRepository
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.ProcessCategoryService
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
          pathEndOrSingleSlash {
            get {
              parameter(Symbol("isFragment").as[Boolean]) { isFragment =>
                complete(
                  fragmentRepository.fetchLatestFragments(processingType).map { fragments =>
                    val componentsUiConfig = ComponentsUiConfigParser.parse(processingTypeData.modelData.modelConfig)
                    val modelDefinitionWithBuiltInComponentsAndFragments = new ModelDefinitionEnricher(
                      componentsUiConfig,
                      processingTypeData.modelData.modelClassLoader.classLoader
                    ).enrichModelDefinitionWithBuiltInComponentsAndFragments(
                      processingTypeData.staticModelDefinition,
                      isFragment,
                      fragments
                    )
                    UIProcessObjectsFactory.prepareUIProcessObjects(
                      modelDefinitionWithBuiltInComponentsAndFragments,
                      processingTypeData.modelData,
                      processingTypeData.deploymentManager,
                      user,
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
