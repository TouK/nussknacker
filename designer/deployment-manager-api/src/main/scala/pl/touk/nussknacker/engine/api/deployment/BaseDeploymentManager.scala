package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.deployment.DeploymentManagerScenarioActivityHandling.NoManagerSpecificScenarioActivities
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleProcessStateDefinitionManager
import pl.touk.nussknacker.engine.deployment.CustomActionDefinition

trait BaseDeploymentManager extends DeploymentManager {

  override def processStateDefinitionManager: ProcessStateDefinitionManager = SimpleProcessStateDefinitionManager

  override def close(): Unit = {}

  override def customActionsDefinitions: List[CustomActionDefinition] = List.empty

  override def scenarioActivityHandling: DeploymentManagerScenarioActivityHandling = NoManagerSpecificScenarioActivities

}
