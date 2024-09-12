package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.{Directives, Route}
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.newactivity.ScenarioActivityService
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext

class ActivityInfoResources(
    protected val processService: ProcessService,
    scenarioActivityServices: ProcessingTypeDataProvider[ScenarioActivityService, _]
)(implicit val ec: ExecutionContext)
    extends Directives
    with FailFastCirceSupport
    with RouteWithUser
    with ProcessDirectives
    with LazyLogging {

  def securedRoute(implicit user: LoggedUser): Route = {
    pathPrefix("activityInfo" / ProcessNameSegment) { processName =>
      (post & processDetailsForName(processName)) { processDetails =>
        entity(as[ScenarioGraph]) { scenarioGraph =>
          val scenarioTestService = scenarioActivityServices.forProcessingTypeUnsafe(processDetails.processingType)
          path("activityParameters") {
            complete {
              scenarioTestService.getActivityParameters(scenarioGraph, processName, processDetails.isFragment)
            }
          }
        }
      }
    }
  }

}
