package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server._
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.definition.test.TestingCapabilities
import pl.touk.nussknacker.ui.api.utils.ScenarioDetailsOps._
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
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
    with ProcessDirectives
    with LazyLogging {

  implicit val timeout: Timeout = Timeout(1 minute)

  def securedRoute(implicit user: LoggedUser): Route = {
    // TODO: is Write enough here?
    pathPrefix("testInfo" / ProcessNameSegment) { processName =>
      (post & processDetailsForName(processName)) { processDetails =>
        entity(as[ScenarioGraph]) { scenarioGraph =>
          canDeploy(processDetails.idWithNameUnsafe) {
            val scenarioTestService = scenarioTestServices.forProcessingTypeUnsafe(processDetails.processingType)
            path("capabilities") {
              complete {
                scenarioTestService.getTestingCapabilities(
                  scenarioGraph,
                  processName,
                  processDetails.isFragment,
                  processDetails.scenarioLabels
                )
              }
            } ~ path("testParameters") {
              complete {
                scenarioTestService.testUISourceParametersDefinition(
                  scenarioGraph,
                  processName,
                  processDetails.isFragment,
                  processDetails.scenarioLabels
                )
              }
            } ~ path("generate" / IntNumber) { testSampleSize =>
              complete {
                scenarioTestService.generateData(
                  scenarioGraph,
                  processName,
                  processDetails.isFragment,
                  processDetails.scenarioLabels,
                  testSampleSize
                ) match {
                  case Left(error) =>
                    logger.error(s"Error during generation of test data: $error")
                    HttpResponse(StatusCodes.BadRequest, entity = error)
                  case Right(rawScenarioTestData) =>
                    HttpResponse(entity = rawScenarioTestData.content)
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
