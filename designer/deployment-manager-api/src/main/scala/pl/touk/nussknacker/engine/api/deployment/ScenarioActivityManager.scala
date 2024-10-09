package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.deployment.ScenarioActivityManager.ModificationResult

import scala.concurrent.Future

trait ScenarioActivityManager {

  def saveActivity(
      scenarioActivity: ScenarioActivity
  ): Future[Unit]

  def modifyActivity(
      scenarioActivityId: ScenarioActivityId,
      modify: ScenarioActivity => ScenarioActivity,
  ): Future[ModificationResult]

}

object ScenarioActivityManager {
  sealed trait ModificationResult

  object ModificationResult {
    case object Success extends ModificationResult
    case object Failure extends ModificationResult
  }

}

object NoOpScenarioActivityManager extends ScenarioActivityManager {

  def saveActivity(
      scenarioActivity: ScenarioActivity
  ): Future[Unit] = Future.unit

  def modifyActivity(
      scenarioActivityId: ScenarioActivityId,
      modify: ScenarioActivity => ScenarioActivity,
  ): Future[ModificationResult] = Future.successful(ModificationResult.Success)

}
