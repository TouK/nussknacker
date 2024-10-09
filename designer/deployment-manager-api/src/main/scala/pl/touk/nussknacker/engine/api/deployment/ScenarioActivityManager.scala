package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.deployment.ScenarioActivityManager.ScenarioActivityModificationResult

import scala.concurrent.Future

trait ScenarioActivityManager {

  def saveActivity(
      scenarioActivity: ScenarioActivity
  ): Future[Unit]

  def modifyActivity(
      scenarioActivityId: ScenarioActivityId,
      modify: ScenarioActivity => ScenarioActivity,
  ): Future[ScenarioActivityModificationResult]

}

object ScenarioActivityManager {
  sealed trait ScenarioActivityModificationResult

  object ScenarioActivityModificationResult {
    case object Success extends ScenarioActivityModificationResult
    case object Failure extends ScenarioActivityModificationResult
  }

}

object NoOpScenarioActivityManager extends ScenarioActivityManager {

  def saveActivity(
      scenarioActivity: ScenarioActivity
  ): Future[Unit] = Future.unit

  def modifyActivity(
      scenarioActivityId: ScenarioActivityId,
      modify: ScenarioActivity => ScenarioActivity,
  ): Future[ScenarioActivityModificationResult] = Future.successful(ScenarioActivityModificationResult.Success)

}
