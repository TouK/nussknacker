package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine.api.deployment.{
  DeploymentRelatedActivity,
  ScenarioActivityId,
  ScenarioActivityManager
}
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner
import pl.touk.nussknacker.ui.process.repository.activities.ScenarioActivityRepository

import scala.concurrent.{ExecutionContext, Future}

class RepositoryBasedScenarioActivityManager(
    repository: ScenarioActivityRepository,
    dbioActionRunner: DBIOActionRunner
)(implicit executionContext: ExecutionContext)
    extends ScenarioActivityManager {

  override def saveActivity(
      scenarioActivity: DeploymentRelatedActivity
  ): Future[Unit] = {
    dbioActionRunner
      .run(repository.addActivity(scenarioActivity))
      .map((_: ScenarioActivityId) => ())
  }

}
