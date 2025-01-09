package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.ui.api.utils.ScenarioDetailsOps.ScenarioWithDetailsOps
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.newactivity.ActionInfoService
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext

class ActionInfoResources(
    protected val processService: ProcessService,
    actionInfoService: ProcessingTypeDataProvider[ActionInfoService, _]
)(implicit val ec: ExecutionContext)
    extends Directives
    with FailFastCirceSupport
    with RouteWithUser
    with ProcessDirectives
    with LazyLogging {

  def securedRoute(implicit user: LoggedUser): Route = {
    pathPrefix("actionInfo" / ProcessNameSegment) { processName =>
      (post & processDetailsForName(processName)) { processDetails =>
        entity(as[ScenarioGraph]) { scenarioGraph =>
          path("actionParameters") {
            complete {
              actionInfoService
                .forProcessingTypeUnsafe(processDetails.processingType)
                .getActionParameters(
                  scenarioGraph,
                  processDetails.processVersionUnsafe,
                  processDetails.isFragment
                )
            }
          }
        }
      }
    }
  }

}
