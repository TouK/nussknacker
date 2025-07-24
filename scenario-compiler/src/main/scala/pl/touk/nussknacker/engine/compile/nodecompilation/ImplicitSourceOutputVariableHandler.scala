package pl.touk.nussknacker.engine.compile.nodecompilation

import pl.touk.nussknacker.engine.api.VariableConstants
import pl.touk.nussknacker.engine.graph.node.{NodeData, WithOptionalOutputVar}
import pl.touk.nussknacker.engine.graph.node.Source

object ImplicitSourceOutputVariableHandler {

  implicit class NodeDataExt(nodeData: NodeData) {

    def outputVariableNameHandlingInputSourceVariableName: Option[String] = {
      nodeData match {
        case _: Source                            => Some(VariableConstants.InputVariableName)
        case withOutputVar: WithOptionalOutputVar => withOutputVar.outputVar
        case _                                    => None
      }
    }

  }

}
