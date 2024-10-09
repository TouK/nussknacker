package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.process.ProcessIdWithName

import scala.concurrent.Future

sealed trait ScenarioActivityHandling

object ScenarioActivityHandling {

  case object AllScenarioActivitiesStoredByNussknacker extends ScenarioActivityHandling

  trait ManagerSpecificScenarioActivitiesStoredByManager extends ScenarioActivityHandling {

    def managerSpecificScenarioActivities(
        processIdWithName: ProcessIdWithName
    ): Future[List[ScenarioActivity]]

  }

}
