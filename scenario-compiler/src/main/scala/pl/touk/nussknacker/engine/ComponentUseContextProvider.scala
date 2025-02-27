package pl.touk.nussknacker.engine

import pl.touk.nussknacker.engine.api.component.NodesDeploymentData.NodeDeploymentData
import pl.touk.nussknacker.engine.api.process.ComponentUseContext

sealed trait ComponentUseContextProvider {
  def toContext(nodeDeploymentData: Option[NodeDeploymentData]): ComponentUseContext
}

object ComponentUseContextProvider {

  case object LiveRuntime extends ComponentUseContextProvider {
    override def toContext(nodeDeploymentData: Option[NodeDeploymentData]): ComponentUseContext =
      ComponentUseContext.LiveRuntime(nodeDeploymentData)
  }

  case object TestRuntime extends ComponentUseContextProvider {
    override def toContext(nodeDeploymentData: Option[NodeDeploymentData]): ComponentUseContext =
      ComponentUseContext.ScenarioTesting
  }

}
