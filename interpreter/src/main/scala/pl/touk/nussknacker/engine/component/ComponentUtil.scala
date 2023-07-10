package pl.touk.nussknacker.engine.component

import pl.touk.nussknacker.engine.api.component
import pl.touk.nussknacker.engine.api.component.ComponentType
import pl.touk.nussknacker.engine.graph.node._

object ComponentUtil {

  def extractComponentType(nodeData: NodeData): Option[component.ComponentType.Value] = nodeData match {
    case _: Source => Some(ComponentType.Source)
    case _: Sink => Some(ComponentType.Sink)
    case _: Filter => Some(ComponentType.Filter)
    case _: Split => Some(ComponentType.Split)
    case _: Switch => Some(ComponentType.Switch)
    case _: Variable => Some(ComponentType.Variable)
    case _: VariableBuilder => Some(ComponentType.MapVariable)
    case _: CustomNodeData => Some(ComponentType.CustomNode)
    case _: Enricher => Some(ComponentType.Enricher)
    case _: Processor => Some(ComponentType.Processor)
    case _: FragmentInput => Some(ComponentType.Fragments)
    case _: FragmentInputDefinition => Some(ComponentType.FragmentInput)
    case _: FragmentOutputDefinition => Some(ComponentType.FragmentOutput)
    case _ => None
  }

  def extractComponentName(nodeData: NodeData): Option[String] = {
    nodeData match {
      case withComponent: WithComponent => Some(withComponent.componentId)
      case _ => None
    }
  }

}
