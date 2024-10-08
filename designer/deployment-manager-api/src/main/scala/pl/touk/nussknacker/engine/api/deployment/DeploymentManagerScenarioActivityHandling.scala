package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.process.ProcessIdWithName

import scala.concurrent.Future

sealed trait DeploymentManagerScenarioActivityHandling

object DeploymentManagerScenarioActivityHandling {

  case object NoManagerSpecificScenarioActivities extends DeploymentManagerScenarioActivityHandling

  trait ManagerSpecificScenarioActivitiesStoredByManager extends DeploymentManagerScenarioActivityHandling {

    def managerSpecificScenarioActivities(
        processIdWithName: ProcessIdWithName
    ): Future[List[ScenarioActivity]]

  }

  trait ManagerSpecificScenarioActivitiesStoredByNussknacker extends DeploymentManagerScenarioActivityHandling {
    def scenarioActivityHandler: ScenarioActivityHandler
  }

  trait ScenarioActivityHandler {

    def saveActivity(
        scenarioActivity: ScenarioActivity
    ): Future[Unit]

    def modifyActivity(
        scenarioActivityId: ScenarioActivityId,
        modify: ScenarioActivity => ScenarioActivity,
    ): Future[Either[String, Unit]]

  }

}
