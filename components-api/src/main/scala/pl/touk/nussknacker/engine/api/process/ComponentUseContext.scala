package pl.touk.nussknacker.engine.api.process

import pl.touk.nussknacker.engine.api.component.NodesDeploymentData.NodeDeploymentData
import pl.touk.nussknacker.engine.api.process.ComponentUseContext.EngineRuntime

/**
  * Specifies the mode a node is used/invoked. It can be one of the following values:
  * <ul>
  * <li>EngineRuntime - component is invoked in real engine, eg. Flink.</li>
  * <li>TestRuntime - component is invoked in test mode to collect results for test data. Can be used to stub real implementation.</li>
  * <li>Validation - used when compiling and validating nodes by Designer. Components should not be invoked in this mode.</li>
  * <li>ServiceQuery - used when component (Service) is invoked by Designer in ServiceQuery.</li>
  * <li>TestDataGeneration - used when compiling, but only for purpose of generating test data. Components should not be invoked in this mode.</li>
  * </ul>
  */
sealed trait ComponentUseContext {

  def deploymentData(): Option[NodeDeploymentData] = this match {
    case EngineRuntime(nodeData) => nodeData
    case _                       => None
  }

}

object ComponentUseContext {
  case class EngineRuntime(nodeData: Option[NodeDeploymentData]) extends ComponentUseContext
  case object TestRuntime                                        extends ComponentUseContext
  case object Validation                                         extends ComponentUseContext
  case object ServiceQuery                                       extends ComponentUseContext
  case object TestDataGeneration                                 extends ComponentUseContext

}
