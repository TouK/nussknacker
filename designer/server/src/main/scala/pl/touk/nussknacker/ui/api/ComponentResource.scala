package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.engine.api.component.ComponentId
import pl.touk.nussknacker.restmodel.component.ComponentUsagesInScenario
import pl.touk.nussknacker.ui.api.NuDesignerErrorToHttp.toResponseEither
import pl.touk.nussknacker.ui.component.ComponentService
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext

class ComponentResource(componentService: ComponentService)(implicit val ec: ExecutionContext)
    extends Directives
    with FailFastCirceSupport
    with RouteWithUser {

  override def securedRoute(implicit user: LoggedUser): Route =
    encodeResponse {
      path("components") {
        get {
          complete {
            componentService.getComponentsList
          }
        }
      } ~ path("components" / Segment / "usages") { componentId =>
        get {
          complete {
            componentService
              .getComponentUsages(ComponentId(componentId))
              .map(toResponseEither[List[ComponentUsagesInScenario]])
          }
        }
      }
    }

}
