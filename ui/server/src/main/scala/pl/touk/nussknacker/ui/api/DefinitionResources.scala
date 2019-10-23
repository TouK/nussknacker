package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.ui.definition
import pl.touk.nussknacker.ui.definition.UIProcessObjects
import pl.touk.nussknacker.ui.process.{ProcessObjectsFinder, ProcessTypesForCategories}
import pl.touk.nussknacker.ui.process.subprocess.SubprocessRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.EspPathMatchers

import scala.concurrent.ExecutionContext

class DefinitionResources(modelData: Map[ProcessingType, ModelData],
                          subprocessRepository: SubprocessRepository,
                          typesForCategories: ProcessTypesForCategories)
                         (implicit ec: ExecutionContext)
  extends Directives with FailFastCirceSupport with EspPathMatchers with RouteWithUser {

  def route(implicit user: LoggedUser) : Route = encodeResponse {
    //TODO maybe always return data for all subprocesses versions instead of fetching just one-by-one?
    path("processDefinitionData" / Segment) { (processingType) =>
      parameter('isSubprocess.as[Boolean]) { (isSubprocess) =>
        post { // POST - because there is sending complex subprocessVersions parameter
          entity(as[Map[String, Long]]) { subprocessVersions =>
              modelData.get(processingType).map { modelDataForType =>
                val subprocesses = subprocessRepository.loadSubprocesses(subprocessVersions)
                complete {
                  UIProcessObjects.prepareUIProcessObjects(modelDataForType, user, subprocesses, isSubprocess, typesForCategories)
                }
              }.getOrElse {
                complete {
                  HttpResponse(status = StatusCodes.NotFound, entity = s"Processing type: $processingType not found")
                }
              }
          }
        }
      }
    } ~ path("processDefinitionData" / "componentIds") {
      get {
        complete {
          val subprocessIds = subprocessRepository.loadSubprocesses().map(_.canonical.metaData.id).toList
          ProcessObjectsFinder.componentIds(modelData.values.map(_.processDefinition).toList, subprocessIds)
        }
      }
    } ~ path("processDefinitionData" / "services") {
      get {
        complete {
          modelData.mapValues(_.processDefinition.services.mapValues(definition.UIObjectDefinition(_)))
        }
      }
    }
  }

}


