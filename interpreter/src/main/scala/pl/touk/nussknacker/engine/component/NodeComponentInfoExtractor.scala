package pl.touk.nussknacker.engine.component

import pl.touk.nussknacker.engine.api.component.{
  BuiltInComponentNames,
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
      case _: compilednode.Filter    => Some(ComponentInfo(BuiltInComponentNames.Filter, RealComponentType.BuiltIn))
      case _: compilednode.SplitNode => Some(ComponentInfo(BuiltInComponentNames.Split, RealComponentType.BuiltIn))
      case _: compilednode.Switch    => Some(ComponentInfo(BuiltInComponentNames.Choice, RealComponentType.BuiltIn))
      case compilednode.VariableBuilder(_, _, Left(_), _) =>
        Some(ComponentInfo(BuiltInComponentNames.Variable, RealComponentType.BuiltIn))
      case compilednode.VariableBuilder(_, _, Right(_), _) =>
        Some(ComponentInfo(BuiltInComponentNames.RecordVariable, RealComponentType.BuiltIn))
      case customNode: compilednode.CustomNode => Some(ComponentInfo(customNode.ref, RealComponentType.CustomComponent))
      case customNode: compilednode.EndingCustomNode =>
        Some(ComponentInfo(customNode.ref, RealComponentType.CustomComponent))
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
      case _: scenarionode.Filter      => Some(ComponentInfo(BuiltInComponentNames.Filter, RealComponentType.BuiltIn))
      case _: scenarionode.Split       => Some(ComponentInfo(BuiltInComponentNames.Split, RealComponentType.BuiltIn))
      case _: scenarionode.Switch      => Some(ComponentInfo(BuiltInComponentNames.Choice, RealComponentType.BuiltIn))
      case _: scenarionode.Variable =>
        Some(ComponentInfo(BuiltInComponentNames.Variable, RealComponentType.BuiltIn))
      case _: scenarionode.VariableBuilder =>
        Some(ComponentInfo(BuiltInComponentNames.RecordVariable, RealComponentType.BuiltIn))
      case custom: scenarionode.CustomNodeData =>
        Some(ComponentInfo(custom.componentId, RealComponentType.CustomComponent))
      case service: scenarionode.Enricher  => Some(ComponentInfo(service.componentId, RealComponentType.Service))
      case service: scenarionode.Processor => Some(ComponentInfo(service.componentId, RealComponentType.Service))
      case _: scenarionode.FragmentInputDefinition =>
        Some(ComponentInfo(BuiltInComponentNames.FragmentInputDefinition, RealComponentType.BuiltIn))
      case _: scenarionode.FragmentOutputDefinition =>
        Some(ComponentInfo(BuiltInComponentNames.FragmentOutputDefinition, RealComponentType.BuiltIn))
      case fragment: scenarionode.FragmentInput => Some(ComponentInfo(fragment.componentId, RealComponentType.Fragment))
      case _: scenarionode.FragmentUsageOutput  => None
      case _: scenarionode.BranchEndData        => None
    }
  }

}
