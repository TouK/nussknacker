package pl.touk.nussknacker.engine

import pl.touk.nussknacker.engine.ComponentUseCase._
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData.NodeDeploymentData
import pl.touk.nussknacker.engine.api.process.ComponentUseContext

sealed trait ComponentUseCase {

  def toContext(nodeDeploymentData: Option[NodeDeploymentData]): ComponentUseContext = this match {
    case EngineRuntime      => ComponentUseContext.EngineRuntime(nodeDeploymentData)
    case TestRuntime        => ComponentUseContext.TestRuntime
    case Validation         => ComponentUseContext.Validation
    case ServiceQuery       => ComponentUseContext.ServiceQuery
    case TestDataGeneration => ComponentUseContext.TestDataGeneration
  }

}

object ComponentUseCase {
  case object EngineRuntime      extends ComponentUseCase
  case object TestRuntime        extends ComponentUseCase
  case object Validation         extends ComponentUseCase
  case object ServiceQuery       extends ComponentUseCase
  case object TestDataGeneration extends ComponentUseCase
}
