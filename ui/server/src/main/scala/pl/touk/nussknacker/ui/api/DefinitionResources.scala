package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directives, Route}
import pl.touk.http.argonaut.{Argonaut62Support, JsonMarshaller}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.ui.definition
import pl.touk.nussknacker.ui.definition.{UIObjectDefinition, UIProcessObjects}
import pl.touk.nussknacker.ui.process.ProcessObjectsFinder
import pl.touk.nussknacker.ui.process.subprocess.SubprocessRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.EspPathMatchers

import scala.concurrent.ExecutionContext

class DefinitionResources(modelData: Map[ProcessingType, ModelData],
                          subprocessRepository: SubprocessRepository)
                         (implicit ec: ExecutionContext, jsonMarshaller: JsonMarshaller)
  extends Directives with Argonaut62Support with EspPathMatchers with RouteWithUser {

  import argonaut.ArgonautShapeless._
  import pl.touk.nussknacker.ui.codec.UiCodecs._

  def route(implicit user: LoggedUser) : Route = encodeResponse {
    //TODO maybe always return data for all subprocesses versions instead of fetching just one-by-one?
    path("processDefinitionData" / Segment) { (processingType) =>
      parameter('isSubprocess.as[Boolean]) { (isSubprocess) =>
        post { // POST - because there is sending complex subprocessVersions parameter
          entity(as[Map[String, Long]]) { subprocessVersions =>
            complete {
              val response: HttpResponse = modelData.get(processingType).map { modelDataForType =>
                val subprocesses = subprocessRepository.loadSubprocesses(subprocessVersions)
                val result = UIProcessObjects.prepareUIProcessObjects(modelDataForType, user, subprocesses, isSubprocess)
                HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, result.asJson.toString()))
              }.getOrElse {
                HttpResponse(status = StatusCodes.NotFound, entity = s"Processing type: $processingType not found")
              }
              response
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
          val result = modelData.mapValues(_.processDefinition.services.mapValues(definition.UIObjectDefinition(_)))
          HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, result.asJson.toString()))
        }
      }
    }
  }

}


