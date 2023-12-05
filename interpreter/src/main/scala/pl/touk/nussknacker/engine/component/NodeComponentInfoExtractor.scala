package pl.touk.nussknacker.engine.component

import pl.touk.nussknacker.engine.api.component.{
  BaseComponentNames,
  ComponentInfo,
  NodeComponentInfo,
  RealComponentType
}
import pl.touk.nussknacker.engine.compiledgraph.{node => compilednode}
import pl.touk.nussknacker.engine.graph.{node => scenarionode}

// TODO this logic should be in one place with DefaultComponentIdProvider
object NodeComponentInfoExtractor {

  def fromCompiledNode(node: compilednode.Node): NodeComponentInfo = {
    val componentInfo = extractComponentInfo(node)
    NodeComponentInfo(node.id, componentInfo)
  }

  def fromScenarioNode(nodeData: scenarionode.NodeData): NodeComponentInfo = {
    val componentInfo = extractComponentInfo(nodeData)
    NodeComponentInfo(nodeData.id, componentInfo)
  }

  private def extractComponentInfo(node: compilednode.Node) = {
    node match {
      case compilednode.Source(_, Some(ref), _) => Some(ComponentInfo(ref, RealComponentType.Source))
      case compilednode.Source(_, None, _)      => None
      case sink: compilednode.Sink              => Some(ComponentInfo(sink.ref, RealComponentType.Sink))
      case _: compilednode.Filter    => Some(ComponentInfo(BaseComponentNames.Filter, RealComponentType.Base))
      case _: compilednode.SplitNode => Some(ComponentInfo(BaseComponentNames.Split, RealComponentType.Base))
      case _: compilednode.Switch    => Some(ComponentInfo(BaseComponentNames.Choice, RealComponentType.Base))
      case compilednode.VariableBuilder(_, _, Left(_), _) =>
        Some(ComponentInfo(BaseComponentNames.Variable, RealComponentType.Base))
      case compilednode.VariableBuilder(_, _, Right(_), _) =>
        Some(ComponentInfo(BaseComponentNames.RecordVariable, RealComponentType.Base))
      case customNode: compilednode.CustomNode => Some(ComponentInfo(customNode.ref, RealComponentType.CustomNode))
      case customNode: compilednode.EndingCustomNode =>
        Some(ComponentInfo(customNode.ref, RealComponentType.CustomNode))
      case service: compilednode.Enricher        => Some(ComponentInfo(service.service.id, RealComponentType.Service))
      case service: compilednode.Processor       => Some(ComponentInfo(service.service.id, RealComponentType.Service))
      case service: compilednode.EndingProcessor => Some(ComponentInfo(service.service.id, RealComponentType.Service))
      case _: compilednode.FragmentOutput        => None
      case _: compilednode.FragmentUsageStart    => None
      case _: compilednode.FragmentUsageEnd      => None
      case _: compilednode.BranchEnd             => None
    }
  }

  private def extractComponentInfo(node: scenarionode.NodeData) = {
    node match {
      case source: scenarionode.Source => Some(ComponentInfo(source.componentId, RealComponentType.Source))
      case sink: scenarionode.Sink     => Some(ComponentInfo(sink.componentId, RealComponentType.Sink))
      case _: scenarionode.Filter      => Some(ComponentInfo(BaseComponentNames.Filter, RealComponentType.Base))
      case _: scenarionode.Split       => Some(ComponentInfo(BaseComponentNames.Split, RealComponentType.Base))
      case _: scenarionode.Switch      => Some(ComponentInfo(BaseComponentNames.Choice, RealComponentType.Base))
      case _: scenarionode.Variable    => Some(ComponentInfo(BaseComponentNames.Variable, RealComponentType.Base))
      case _: scenarionode.VariableBuilder =>
        Some(ComponentInfo(BaseComponentNames.RecordVariable, RealComponentType.Base))
      case custom: scenarionode.CustomNodeData => Some(ComponentInfo(custom.componentId, RealComponentType.CustomNode))
      case service: scenarionode.Enricher      => Some(ComponentInfo(service.componentId, RealComponentType.Service))
      case service: scenarionode.Processor     => Some(ComponentInfo(service.componentId, RealComponentType.Service))
      case _: scenarionode.FragmentInputDefinition  => None
      case _: scenarionode.FragmentOutputDefinition => None
      case fragment: scenarionode.FragmentInput =>
        Some(ComponentInfo(fragment.componentId, RealComponentType.Fragments))
      case _: scenarionode.FragmentUsageOutput => None
      case _: scenarionode.BranchEndData       => None
    }
  }

}
