package pl.touk.nussknacker.engine.api.deployment

import scala.concurrent.Future

trait ScenarioActivityManager {

  def saveActivity(
      activity: DeploymentRelatedActivity
  ): Future[Unit]

}

object NoOpScenarioActivityManager extends ScenarioActivityManager {

  def saveActivity(
      activity: DeploymentRelatedActivity
  ): Future[Unit] = Future.unit

}
