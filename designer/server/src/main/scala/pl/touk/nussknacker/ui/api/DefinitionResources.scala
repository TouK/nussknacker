package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.engine.{ModelData, ProcessingTypeData}
import pl.touk.nussknacker.ui.definition.UIProcessObjectsFactory
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.subprocess.SubprocessRepository
import pl.touk.nussknacker.ui.process.{ProcessCategoryService, ProcessObjectsFinder}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.EspPathMatchers

import scala.concurrent.ExecutionContext

class DefinitionResources(modelDataProvider: ProcessingTypeDataProvider[ModelData, _],
                          processingTypeDataProvider: ProcessingTypeDataProvider[ProcessingTypeData, _],
                          subprocessRepository: SubprocessRepository,
                          processCategoryService: ProcessCategoryService)
                         (implicit ec: ExecutionContext)
  extends Directives with FailFastCirceSupport with EspPathMatchers with RouteWithUser {

  private val dictResources = new DictResources

  def securedRoute(implicit user: LoggedUser) : Route = encodeResponse {
    path("processDefinitionData" / "componentIds") {
      get {
        complete {
          val subprocessIds = subprocessRepository.loadSubprocessIds()
          ProcessObjectsFinder.componentIds(modelDataProvider.all.values.map(_.processWithObjectsDefinition).toList, subprocessIds)
        }
      }
    } ~ pathPrefix("processDefinitionData" / Segment) { processingType =>
      processingTypeDataProvider.forType(processingType).map { processingTypeData =>
        //TODO maybe always return data for all subprocesses versions instead of fetching just one-by-one?
        pathEndOrSingleSlash {
          get {
            parameter(Symbol("isSubprocess").as[Boolean]) { (isSubprocess) =>
              val subprocesses = subprocessRepository.loadSubprocesses(Map.empty)
              complete(
                UIProcessObjectsFactory.prepareUIProcessObjects(
                  processingTypeData.modelData,
                  processingTypeData.deploymentManager,
                  processingTypeData.typeSpecificInitialData,
                  user,
                  subprocesses,
                  isSubprocess,
                  processCategoryService,
                  processingTypeData.typeSpecificPropertiesConfig,
                  processingTypeData.additionalPropertiesConfig,
                  processingType)
              )
            }
          }
        } ~ dictResources.route(processingTypeData.modelData)
      }.getOrElse {
        complete(HttpResponse(status = StatusCodes.NotFound, entity = s"Scenario type: $processingType not found"))
      }
    }
  }
}
