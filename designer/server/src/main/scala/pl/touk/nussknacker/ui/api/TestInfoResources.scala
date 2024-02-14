package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server._
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.definition.test.TestingCapabilities
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.test.ScenarioTestService
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class TestInfoResources(
    val processAuthorizer: AuthorizeProcess,
    protected val processService: ProcessService,
    scenarioTestServices: ProcessingTypeDataProvider[ScenarioTestService, _]
)(implicit val ec: ExecutionContext)
    extends Directives
    with FailFastCirceSupport
    with RouteWithUser
    with AuthorizeProcessDirectives
    with ProcessDirectives {

  implicit val timeout: Timeout = Timeout(1 minute)

  def securedRoute(implicit user: LoggedUser): Route = {
    // TODO: is Write enough here?
    pathPrefix("testInfo" / ProcessNameSegment) { processName =>
      (post & processDetailsForName(processName)) { processDetails =>
        entity(as[ScenarioGraph]) { scenarioGraph =>
          canDeploy(processDetails.idWithNameUnsafe) {
            val scenarioTestService = scenarioTestServices.forTypeUnsafe(processDetails.processingType)
            path("capabilities") {
              complete {
                scenarioTestService.getTestingCapabilities(scenarioGraph, processName, processDetails.isFragment)
              }
            } ~ path("testParameters") {
              complete {
                scenarioTestService.testParametersDefinition(scenarioGraph, processName, processDetails.isFragment)
              }
            } ~ path("generate" / IntNumber) { testSampleSize =>
              complete {
                scenarioTestService.generateData(
                  scenarioGraph,
                  processName,
                  processDetails.isFragment,
                  testSampleSize
                ) match {
                  case Left(error)                => HttpResponse(StatusCodes.BadRequest, entity = error)
                  case Right(rawScenarioTestData) => HttpResponse(entity = rawScenarioTestData.content)
                }
              }
            }
          } ~ path("capabilities") {
            complete(TestingCapabilities.Disabled)
          }
        }
      }
    }
  }

}
