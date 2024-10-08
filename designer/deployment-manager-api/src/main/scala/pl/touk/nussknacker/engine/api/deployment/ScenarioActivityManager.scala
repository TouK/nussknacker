package pl.touk.nussknacker.engine.api.deployment

import scala.concurrent.Future

trait ScenarioActivityManager {

  def saveActivity(
      scenarioActivity: ScenarioActivity
  ): Future[Unit]

  def modifyActivity(
      scenarioActivityId: ScenarioActivityId,
      modify: ScenarioActivity => ScenarioActivity,
  ): Future[Either[String, Unit]]

}

// This stub is in API module because we don't want to extract deployment-manager-tests-utils module
class ScenarioActivityManagerStub extends ScenarioActivityManager {

  def saveActivity(
      scenarioActivity: ScenarioActivity
  ): Future[Unit] = Future.unit

  def modifyActivity(
      scenarioActivityId: ScenarioActivityId,
      modify: ScenarioActivity => ScenarioActivity,
  ): Future[Either[String, Unit]] = Future.successful(Right(()))

}
