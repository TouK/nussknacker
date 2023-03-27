package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.ui.process.ProcessStateDefinitionService
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext

class StatusResources(stateDefinitionService: ProcessStateDefinitionService)
                     (implicit val ec: ExecutionContext)
  extends Directives
    with FailFastCirceSupport
    with RouteWithUser {

  def securedRoute(implicit user: LoggedUser): Route = {
    encodeResponse {
      path("statusDefinitions") {
        get {
          complete {
            stateDefinitionService.fetchStateDefinitions
          }
        }
      }
    }
  }

}
