package pl.touk.nussknacker.engine.component

import pl.touk.nussknacker.engine.api.component.ComponentType
import pl.touk.nussknacker.engine.api.component.NodeComponentInfo
import pl.touk.nussknacker.engine.compiledgraph.node._
import pl.touk.nussknacker.engine.graph.node.{NodeData, WithComponent}

// TODO this logic should be in one place with DefaultComponentIdProvider
object NodeComponentInfoExtractor {

  def fromNode(node: Node): NodeComponentInfo = {
    // warning: this logic should be kept synchronized with DefaultComponentIdProvider
    // TODO: Maybe compiledgraph.node should have componentId field or WihtCompoment trait? Just like NodeData
    node match {
      case source: Source => NodeComponentInfo(node.id, source.ref.getOrElse("source"), ComponentType.Source)
      case sink: Sink => NodeComponentInfo(node.id, sink.ref, ComponentType.Sink)
      case _: Filter => NodeComponentInfo.forBaseNode(node.id, ComponentType.Filter)
      case _: SplitNode => NodeComponentInfo.forBaseNode(node.id, ComponentType.Split)
      case _: Switch => NodeComponentInfo.forBaseNode(node.id, ComponentType.Switch)
      case _: VariableBuilder => NodeComponentInfo.forBaseNode(node.id, ComponentType.Variable)
      case CustomNode(_, _) | EndingCustomNode(_) => NodeComponentInfo(node.id, "customNode", ComponentType.CustomNode)
      case enricher: Enricher => NodeComponentInfo(node.id, enricher.service.id, ComponentType.Enricher)
      case processor: Processor => NodeComponentInfo(node.id, processor.service.id, ComponentType.Processor)
      case endingProcessor: EndingProcessor => NodeComponentInfo(node.id, endingProcessor.service.id, ComponentType.Processor)
      case _: SubprocessStart => NodeComponentInfo.forBaseNode(node.id, ComponentType.FragmentInput)
      case _: SubprocessEnd => NodeComponentInfo.forBaseNode(node.id, ComponentType.FragmentOutput)
      case _: BranchEnd => NodeComponentInfo(node.id, None)
    }
  }

  def fromNodeData(nodeData: NodeData): NodeComponentInfo = {
    val maybeComponentType = ComponentType.fromNodeData(nodeData)
    (nodeData, maybeComponentType) match {
      case (withComponent: WithComponent, Some(componentType)) => NodeComponentInfo(nodeData.id, withComponent.componentId, componentType)
      case (_, Some(componentType)) if ComponentType.isBaseComponent(componentType) => NodeComponentInfo.forBaseNode(nodeData.id, componentType)
      case _ => NodeComponentInfo(nodeData.id, None)
    }
  }
}
