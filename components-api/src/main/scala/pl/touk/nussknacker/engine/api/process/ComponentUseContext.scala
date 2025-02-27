package pl.touk.nussknacker.engine.api.process

import pl.touk.nussknacker.engine.api.component.NodesDeploymentData.NodeDeploymentData

/**
  * Specifies the mode a node is used/invoked. It can be one of the following values:
  * <ul>
  * <li>LiveRuntime - component is invoked in live engine, eg. Flink.</li>
  * <li>ScenarioTesting - component is invoked in test mode to collect results for test data. Can be used to stub real implementation.</li>
  * </ul>
  */
sealed trait ComponentUseContext {
  def deploymentData(): Option[NodeDeploymentData]
}

object ComponentUseContext {

  case class LiveRuntime(nodeData: Option[NodeDeploymentData]) extends ComponentUseContext {
    override def deploymentData(): Option[NodeDeploymentData] = nodeData
  }

  case object ScenarioTesting extends ComponentUseContext {
    override def deploymentData(): Option[NodeDeploymentData] = None
  }

}
