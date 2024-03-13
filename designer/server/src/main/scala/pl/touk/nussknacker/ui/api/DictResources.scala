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
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.ui.api.DictResources.{DictListElement, DictListRequest}
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util.NuPathMatchers

import scala.concurrent.ExecutionContext

object DictResources {
  @JsonCodec
  case class DictListRequest(expectedType: TypingResultJson)

  @JsonCodec
  case class TypingResultJson(value: Json)

  @JsonCodec(encodeOnly = true)
  case class DictListElement(name: String, typ: TypingResult)
}

class DictResources(
    dictDataProvider: ProcessingTypeDataProvider[(DictQueryService, Map[String, DictDefinition], ClassLoader), _]
)(implicit ec: ExecutionContext)
    extends Directives
    with FailFastCirceSupport
    with NuPathMatchers
    with RouteWithUser {

  def securedRoute(implicit user: LoggedUser): Route = encodeResponse {
    pathPrefix("dicts" / Segment) { processingType =>
      dictDataProvider
        .forType(processingType)
        .map { case (dictQueryService, dictionaries, classLoader) =>
          pathEndOrSingleSlash {
            post {
              entity(as[DictListRequest]) { dictListRequest =>
                val decoder = new TypingResultDecoder(ClassUtils.forName(_, classLoader)).decodeTypingResults

                decoder.decodeJson(dictListRequest.expectedType.value) match {
                  case Left(failure) =>
                    complete(
                      HttpResponse(status = StatusCodes.BadRequest, entity = s"Malformed expected type: $failure")
                    )
                  case Right(expectedType) =>
                    complete {
                      ToResponseMarshallable(
                        dictionaries
                          .map { case (id, dictDefinition) => DictListElement(id, dictDefinition.valueType(id)) }
                          .filter {
                            _.typ.canBeSubclassOf(expectedType)
                          }
                      )
                    }
                }
              }
            }
          } ~ path(Segment / "entry") { dictId =>
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
          }
        }
        .getOrElse {
          complete(HttpResponse(status = StatusCodes.NotFound, entity = s"Scenario type: $processingType not found"))
        }
    }
  }

}
