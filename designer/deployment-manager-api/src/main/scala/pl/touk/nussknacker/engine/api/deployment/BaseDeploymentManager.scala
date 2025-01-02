package pl.touk.nussknacker.engine.api.deployment

import pl.touk.nussknacker.engine.api.deployment.simple.SimpleProcessStateDefinitionManager

trait BaseDeploymentManager extends DeploymentManager {

  override def processStateDefinitionManager: ProcessStateDefinitionManager = SimpleProcessStateDefinitionManager

  override def close(): Unit = {}

}
