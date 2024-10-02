package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.deployment.simple.SimpleProcessStateDefinitionManager
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.deployment.CustomActionDefinition

import scala.concurrent.Future

trait BaseDeploymentManager extends DeploymentManager {

  override def processStateDefinitionManager: ProcessStateDefinitionManager = SimpleProcessStateDefinitionManager

  override def close(): Unit = {}

  override def customActionsDefinitions: List[CustomActionDefinition] = List.empty

  override def managerSpecificScenarioActivities(processIdWithName: ProcessIdWithName): Future[List[ScenarioActivity]] =
    Future.successful(Nil)

}
