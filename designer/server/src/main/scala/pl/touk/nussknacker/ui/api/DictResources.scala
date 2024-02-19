package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.engine.api.dict.DictQueryService

import scala.concurrent.ExecutionContext

class DictResources(implicit ec: ExecutionContext) extends Directives with FailFastCirceSupport {

  def route(dictQueryService: DictQueryService): Route =
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
    }

}
