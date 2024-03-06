package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import io.circe.generic.JsonCodec
import org.springframework.util.ClassUtils
import pl.touk.nussknacker.engine.api.dict.{DictDefinition, DictQueryService}
import pl.touk.nussknacker.engine.api.typed.TypingResultDecoder
import pl.touk.nussknacker.ui.api.DictResources.DictListRequest

import scala.concurrent.ExecutionContext

object DictResources {
  @JsonCodec
  case class DictListRequest(expectedType: TypingResultJson)

  @JsonCodec
  case class TypingResultJson(value: Json)
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
          val decoder = new TypingResultDecoder(ClassUtils.forName(_, classLoader)).decodeTypingResults

          decoder.decodeJson(dictListRequest.expectedType.value) match {
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
