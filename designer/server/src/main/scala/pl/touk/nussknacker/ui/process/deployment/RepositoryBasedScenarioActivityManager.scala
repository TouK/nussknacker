package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine.api.deployment.ScenarioActivityManager.ModificationResult
import pl.touk.nussknacker.engine.api.deployment.{ScenarioActivity, ScenarioActivityId, ScenarioActivityManager}
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository.ModifyActivityError

import scala.concurrent.{ExecutionContext, Future}

class RepositoryBasedScenarioActivityManager(
    repository: ScenarioActivityRepository,
    dbioActionRunner: DBIOActionRunner
)(implicit executionContext: ExecutionContext)
    extends ScenarioActivityManager {

  override def saveActivity(
      scenarioActivity: ScenarioActivity
  ): Future[Unit] = {
    dbioActionRunner
      .run(repository.addActivity(scenarioActivity))
      .map((_: ScenarioActivityId) => ())
  }

  override def modifyActivity(
      scenarioActivityId: ScenarioActivityId,
      modification: ScenarioActivity => ScenarioActivity
  ): Future[ModificationResult] = dbioActionRunner.run(
    repository
      .modifyActivity(scenarioActivityId, modification)
      .map {
        case Right(_: Unit)               => ModificationResult.Success
        case Left(_: ModifyActivityError) => ModificationResult.Failure
      }
  )

}
