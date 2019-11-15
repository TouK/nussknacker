package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.engine.api.dict.DictQueryService
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext

class DictResources(dictQueryService: DictQueryService)(implicit ec: ExecutionContext) extends Directives with FailFastCirceSupport with RouteWithUser {

  def securedRoute(implicit user: LoggedUser): Route =
    path("dict" / Segment / "entry") { dictId =>
      get {
        parameter("label".as[String]) { labelPattern =>
          complete {
            dictQueryService.queryEntriesByLabel(dictId, labelPattern)
          }
        }
      }
    }

}
