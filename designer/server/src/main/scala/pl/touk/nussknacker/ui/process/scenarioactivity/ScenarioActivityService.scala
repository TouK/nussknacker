package pl.touk.nussknacker.ui.process.scenarioactivity

import cats.data.EitherT
import pl.touk.nussknacker.engine.api.deployment.{ManagerSpecificScenarioActivitiesStoredByManager, ScenarioActivity}
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.ui.process.deployment.DeploymentManagerDispatcher
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository
import pl.touk.nussknacker.ui.process.repository.{DBIOActionRunner, FetchingProcessRepository}
import pl.touk.nussknacker.ui.process.scenarioactivity.ScenarioActivityService.ScenarioActivityFetchError
import pl.touk.nussknacker.ui.process.scenarioactivity.ScenarioActivityService.ScenarioActivityFetchError.NoScenario
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

class ScenarioActivityService(
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
          manager.managerSpecificScenarioActivities(processIdWithName)
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

object ScenarioActivityService {
  sealed trait ScenarioActivityFetchError

  object ScenarioActivityFetchError {
    final case class NoScenario(scenarioName: ProcessName) extends ScenarioActivityFetchError
  }

}
