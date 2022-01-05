package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.engine.{ModelData, ProcessingTypeData}
import pl.touk.nussknacker.ui.definition.UIProcessObjectsFactory
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.{ProcessCategoryService, ProcessObjectsFinder}
import pl.touk.nussknacker.ui.process.subprocess.SubprocessRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.EspPathMatchers

import scala.concurrent.ExecutionContext

class DefinitionResources(modelDataProvider: ProcessingTypeDataProvider[ModelData],
                          processingTypeDataProvider: ProcessingTypeDataProvider[ProcessingTypeData],
                          subprocessRepository: SubprocessRepository,
                          processCategoryService: ProcessCategoryService,
                          linkEncodingConfig: LinkEncodingConfig)
                         (implicit ec: ExecutionContext)
  extends Directives with FailFastCirceSupport with EspPathMatchers with RouteWithUser {

  private val dictResources = new DictResources

  def securedRoute(implicit user: LoggedUser) : Route = encodeResponse {
    path("processDefinitionData" / "componentIds") {
      get {
        complete {
          val subprocessIds = subprocessRepository.loadSubprocesses().map(_.canonical.metaData.id).toList
          ProcessObjectsFinder.componentIds(modelDataProvider.all.values.map(_.processDefinition).toList, subprocessIds)
        }
      }
    } ~ path("processDefinitionData" / "services") {
      get {
        complete {
          modelDataProvider.mapValues(_.processDefinition.services.mapValues(UIProcessObjectsFactory.createUIObjectDefinition(_, processCategoryService))).all
        }
      }
    // TODO: Now we can't have processingType = componentIds or services - we should redesign our API (probably fetch componentIds and services only for given processingType)
    } ~ pathPrefix("processDefinitionData" / Segment) { processingType =>
      processingTypeDataProvider.forType(processingType).map { processingTypeData =>
        //TODO maybe always return data for all subprocesses versions instead of fetching just one-by-one?
        pathEndOrSingleSlash {
          get {
            NuLinkEncoder.nuLinkEncoder(linkEncodingConfig) { implicit encoder =>
              parameter('isSubprocess.as[Boolean]) { (isSubprocess) =>
                val subprocesses = subprocessRepository.loadSubprocesses(Map.empty)
                complete(
                 UIProcessObjectsFactory.prepareUIProcessObjects(
                    processingTypeData.modelData,
                    processingTypeData.deploymentManager,
                    user,
                    subprocesses,
                    isSubprocess,
                    processCategoryService,
                    processingType)
                )
              }
            }
          }
        } ~ dictResources.route(processingTypeData.modelData)
      }.getOrElse {
        complete(HttpResponse(status = StatusCodes.NotFound, entity = s"Scenario type: $processingType not found"))
      }
    }
  }
}
