package pl.touk.nussknacker.engine

import pl.touk.nussknacker.engine.api.component.NodesDeploymentData.NodeDeploymentData
import pl.touk.nussknacker.engine.api.process.ComponentUseContext

sealed trait RuntimeMode {
  def createContext(nodeDeploymentData: Option[NodeDeploymentData]): ComponentUseContext
}

object RuntimeMode {

  case object Live extends RuntimeMode {
    override def createContext(nodeDeploymentData: Option[NodeDeploymentData]): ComponentUseContext =
      ComponentUseContext.LiveRuntime(nodeDeploymentData)
  }

  case object Test extends RuntimeMode {
    override def createContext(nodeDeploymentData: Option[NodeDeploymentData]): ComponentUseContext =
      ComponentUseContext.ScenarioTesting
  }

}
