package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.dict.{DictDefinition, DictQueryService}
import pl.touk.nussknacker.ui.api.DictResources.DictListRequest
import pl.touk.nussknacker.ui.api.NodesApiEndpoints.Dtos.{TypingResultInJson, prepareTypingResultDecoder}

import scala.concurrent.ExecutionContext

object DictResources {
  @JsonCodec
  case class DictListRequest(expectedType: TypingResultInJson)
}

class DictResources(implicit ec: ExecutionContext) extends Directives with FailFastCirceSupport {

  def route(
      dictQueryService: DictQueryService,
      dictionaries: Map[String, DictDefinition],
      classLoader: ClassLoader
  ): Route =
    path("dict" / Segment / "entry") { dictId =>
      get {
        parameter("label".as[String]) { labelPattern =>
          complete {
            dictQueryService
              .queryEntriesByLabel(dictId, labelPattern)
              .map(ToResponseMarshallable(_))
              .valueOr(error =>
                HttpResponse(status = StatusCodes.NotFound, entity = s"Dictionary: ${error.dictId} not found")
              )
          }
        }
      }
    } ~ path("dicts") {
      post {
        entity(as[DictListRequest]) { dictListRequest =>
          prepareTypingResultDecoder(classLoader).decodeJson(dictListRequest.expectedType) match {
            case Left(failure) =>
              complete(HttpResponse(status = StatusCodes.BadRequest, entity = s"Malformed expected type: $failure"))
            case Right(expectedType) =>
              complete {
                dictionaries
                  .filter { case (id, dictDefinition) => dictDefinition.valueType(id).canBeSubclassOf(expectedType) }
                  .keys
                  .toList
              }
          }
        }
      }
    }

}
