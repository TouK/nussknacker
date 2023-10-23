package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.engine.api.component.AdditionalUIConfigProvider
import pl.touk.nussknacker.engine.api.fixedvaluespresets.FixedValuesPresetProvider
import pl.touk.nussknacker.engine.{ModelData, ProcessingTypeData}
import pl.touk.nussknacker.ui.definition.UIProcessObjectsFactory
import pl.touk.nussknacker.ui.process.fragment.FragmentRepository
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.{ProcessCategoryService, ProcessObjectsFinder}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.EspPathMatchers

import scala.concurrent.ExecutionContext

class DefinitionResources(
    modelDataProvider: ProcessingTypeDataProvider[ModelData, _],
    processingTypeDataProvider: ProcessingTypeDataProvider[ProcessingTypeData, _],
    fragmentRepository: FragmentRepository,
    getProcessCategoryService: () => ProcessCategoryService,
    additionalUIConfigProvider: AdditionalUIConfigProvider,
    fixedValuesPresetProvider: FixedValuesPresetProvider
)(implicit ec: ExecutionContext)
    extends Directives
    with FailFastCirceSupport
    with EspPathMatchers
    with RouteWithUser {

  private val dictResources = new DictResources

  def securedRoute(implicit user: LoggedUser): Route = encodeResponse {
    path("processDefinitionData" / "componentIds") {
      get {
        complete {
          val fragmentIds = fragmentRepository.loadFragmentIds()
          ProcessObjectsFinder.componentIds(modelDataProvider.all.values.map(_.modelDefinition).toList, fragmentIds)
        }
      }
    } ~ pathPrefix("processDefinitionData" / Segment) { processingType =>
      processingTypeDataProvider
        .forType(processingType)
        .map { processingTypeData =>
          // TODO maybe always return data for all fragments versions instead of fetching just one-by-one?
          pathEndOrSingleSlash {
            get {
              parameter(Symbol("isFragment").as[Boolean]) { isFragment =>
                val fragments = fragmentRepository.loadFragments(Map.empty)
                complete(
                  UIProcessObjectsFactory.prepareUIProcessObjects(
                    processingTypeData.modelData,
                    processingTypeData.staticObjectsDefinition,
                    processingTypeData.deploymentManager,
                    user,
                    fragments,
                    isFragment,
                    getProcessCategoryService(),
                    processingTypeData.scenarioPropertiesConfig,
                    processingType,
                    additionalUIConfigProvider,
                    fixedValuesPresetProvider
                  )
                )
              }
            }
          } ~ dictResources.route(processingTypeData.modelData)
        }
        .getOrElse {
          complete(HttpResponse(status = StatusCodes.NotFound, entity = s"Scenario type: $processingType not found"))
        }
    }
  }

}
