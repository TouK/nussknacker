package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine.api.deployment.{ScenarioActivity, ScenarioActivityId, ScenarioActivityManager}
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository

import scala.concurrent.{ExecutionContext, Future}

class RepositoryBasedScenarioActivityManager(
    repository: ScenarioActivityRepository,
    dbioActionRunner: DBIOActionRunner
)(implicit executionContext: ExecutionContext)
    extends ScenarioActivityManager {

  override def saveActivity(
      scenarioActivity: ScenarioActivity
  ): Future[Unit] = {
    dbioActionRunner.run(repository.addActivity(scenarioActivity)).map(_ => ())
  }

  override def modifyActivity(
      scenarioActivityId: ScenarioActivityId,
      modification: ScenarioActivity => ScenarioActivity
  ): Future[Either[String, Unit]] = dbioActionRunner.run(
    repository
      .modifyActivity(scenarioActivityId, modification)
      .map(_.left.map(_ => s"Could not modify ScenarioActivity with id=${scenarioActivityId.value}"))
  )

}
