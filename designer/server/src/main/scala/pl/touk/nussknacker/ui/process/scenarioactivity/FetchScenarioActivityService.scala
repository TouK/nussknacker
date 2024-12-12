package pl.touk.nussknacker.ui.process.scenarioactivity

import cats.data.EitherT
import pl.touk.nussknacker.engine.api.deployment.{ManagerSpecificScenarioActivitiesStoredByManager, ScenarioActivity}
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.ui.process.deployment.DeploymentManagerDispatcher
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository
import pl.touk.nussknacker.ui.process.repository.{DBIOActionRunner, FetchingProcessRepository}
import pl.touk.nussknacker.ui.process.scenarioactivity.FetchScenarioActivityService.ScenarioActivityFetchError
import pl.touk.nussknacker.ui.process.scenarioactivity.FetchScenarioActivityService.ScenarioActivityFetchError.NoScenario
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import scala.math.Ordered.orderingToOrdered

// TODO: In current implementation, we do not have a single ScenarioActivity-related service.
// - there is repository, that also encapsulates some very basic logic of validations/conversions
// - there is a logging decorator for that repository
// - the logic of fetching activities is used in multiple places, so it is encapsulated in this FetchScenarioActivityService
// - a complete ScenarioActivityService should be implemented, that would encapsulate logic from this service, and from repository and its decorator
class FetchScenarioActivityService(
    deploymentManagerDispatcher: DeploymentManagerDispatcher,
    scenarioActivityRepository: ScenarioActivityRepository,
    fetchingProcessRepository: FetchingProcessRepository[Future],
    dbioActionRunner: DBIOActionRunner,
)(implicit executionContext: ExecutionContext) {

  def fetchActivities(
      processName: ProcessName,
      after: Option[Instant],
  )(implicit loggedUser: LoggedUser): EitherT[Future, ScenarioActivityFetchError, List[ScenarioActivity]] = {
    for {
      scenarioId <- getScenarioIdByName(processName)
      activities <- EitherT.right(fetchActivities(ProcessIdWithName(scenarioId, processName), after))
    } yield activities
  }

  def fetchActivities(
      processIdWithName: ProcessIdWithName,
      after: Option[Instant],
  )(implicit loggedUser: LoggedUser): Future[List[ScenarioActivity]] = {
    for {
      generalActivities <- dbioActionRunner.run(scenarioActivityRepository.findActivities(processIdWithName.id, after))
      deploymentManager <- deploymentManagerDispatcher.deploymentManager(processIdWithName)
      deploymentManagerSpecificActivities <- deploymentManager match {
        case Some(manager: ManagerSpecificScenarioActivitiesStoredByManager) =>
          manager.managerSpecificScenarioActivities(processIdWithName, after)
        case Some(_) | None =>
          Future.successful(List.empty)
      }
    } yield generalActivities.toList ++ deploymentManagerSpecificActivities
  }

  private def getScenarioIdByName(scenarioName: ProcessName) = {
    EitherT.fromOptionF(
      fetchingProcessRepository.fetchProcessId(scenarioName),
      NoScenario(scenarioName)
    )
  }

}

object FetchScenarioActivityService {
  sealed trait ScenarioActivityFetchError

  object ScenarioActivityFetchError {
    final case class NoScenario(scenarioName: ProcessName) extends ScenarioActivityFetchError
  }

}
