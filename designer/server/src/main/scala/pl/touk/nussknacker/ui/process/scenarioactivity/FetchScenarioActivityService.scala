package pl.touk.nussknacker.ui.process.scenarioactivity

import cats.data.EitherT
import pl.touk.nussknacker.engine.api.deployment.ScenarioActivity
import pl.touk.nussknacker.engine.api.process.{ProcessIdWithName, ProcessName}
import pl.touk.nussknacker.ui.process.repository.{DBIOActionRunner, FetchingProcessRepository}
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository
import pl.touk.nussknacker.ui.process.scenarioactivity.FetchScenarioActivityService.ScenarioActivityFetchError
import pl.touk.nussknacker.ui.process.scenarioactivity.FetchScenarioActivityService.ScenarioActivityFetchError.NoScenario

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}

// TODO: In current implementation, we do not have a single ScenarioActivity-related service.
// - there is repository, that also encapsulates some very basic logic of validations/conversions
// - there is a logging decorator for that repository
// - the logic of fetching activities is used in multiple places, so it is encapsulated in this FetchScenarioActivityService
// - a complete ScenarioActivityService should be implemented, that would encapsulate logic from this service, and from repository and its decorator
class FetchScenarioActivityService(
    scenarioActivityRepository: ScenarioActivityRepository,
    fetchingProcessRepository: FetchingProcessRepository[Future],
    dbioActionRunner: DBIOActionRunner,
)(implicit executionContext: ExecutionContext) {

  def fetchActivities(
      processName: ProcessName,
      after: Option[Instant],
  ): EitherT[Future, ScenarioActivityFetchError, List[ScenarioActivity]] = {
    for {
      scenarioId <- getScenarioIdByName(processName)
      activities <- EitherT.right(fetchActivities(ProcessIdWithName(scenarioId, processName), after))
    } yield activities
  }

  def fetchActivities(
      processIdWithName: ProcessIdWithName,
      after: Option[Instant],
  ): Future[List[ScenarioActivity]] = {
    for {
      generalActivities <- dbioActionRunner.run(scenarioActivityRepository.findActivities(processIdWithName.id, after))
    } yield generalActivities.toList
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
