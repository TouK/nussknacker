package pl.touk.nussknacker.engine.node

import pl.touk.nussknacker.engine.api.component.{BuiltInComponentInfo, ComponentInfo, ComponentType}
import pl.touk.nussknacker.engine.compiledgraph.{node => compilednode}
import pl.touk.nussknacker.engine.graph.{node => scenarionode}

object ComponentInfoExtractor {

  def fromCompiledNode(node: compilednode.Node): Option[ComponentInfo] = {
    node match {
      case compilednode.Source(_, Some(ref), _)            => Some(ComponentInfo(ComponentType.Source, ref))
      case compilednode.Source(_, None, _)                 => None
      case sink: compilednode.Sink                         => Some(ComponentInfo(ComponentType.Sink, sink.ref))
      case _: compilednode.Filter                          => Some(BuiltInComponentInfo.Filter)
      case _: compilednode.SplitNode                       => Some(BuiltInComponentInfo.Split)
      case _: compilednode.Switch                          => Some(BuiltInComponentInfo.Choice)
      case compilednode.VariableBuilder(_, _, Left(_), _)  => Some(BuiltInComponentInfo.Variable)
      case compilednode.VariableBuilder(_, _, Right(_), _) => Some(BuiltInComponentInfo.RecordVariable)
      case customNode: compilednode.CustomNode => Some(ComponentInfo(ComponentType.CustomComponent, customNode.ref))
      case customNode: compilednode.EndingCustomNode =>
        Some(ComponentInfo(ComponentType.CustomComponent, customNode.ref))
      case service: compilednode.Enricher        => Some(ComponentInfo(ComponentType.Service, service.service.id))
      case service: compilednode.Processor       => Some(ComponentInfo(ComponentType.Service, service.service.id))
      case service: compilednode.EndingProcessor => Some(ComponentInfo(ComponentType.Service, service.service.id))
      case _: compilednode.FragmentOutput        => None
      case _: compilednode.FragmentUsageStart    => None
      case _: compilednode.FragmentUsageEnd      => None
      case _: compilednode.BranchEnd             => None
    }
  }

  def fromScenarioNode(node: scenarionode.NodeData): Option[ComponentInfo] = {
    node match {
      case source: scenarionode.Source     => Some(ComponentInfo(ComponentType.Source, source.componentId))
      case sink: scenarionode.Sink         => Some(ComponentInfo(ComponentType.Sink, sink.componentId))
      case _: scenarionode.Filter          => Some(BuiltInComponentInfo.Filter)
      case _: scenarionode.Split           => Some(BuiltInComponentInfo.Split)
      case _: scenarionode.Switch          => Some(BuiltInComponentInfo.Choice)
      case _: scenarionode.Variable        => Some(BuiltInComponentInfo.Variable)
      case _: scenarionode.VariableBuilder => Some(BuiltInComponentInfo.RecordVariable)
      case custom: scenarionode.CustomNodeData =>
        Some(ComponentInfo(ComponentType.CustomComponent, custom.componentId))
      case service: scenarionode.Enricher           => Some(ComponentInfo(ComponentType.Service, service.componentId))
      case service: scenarionode.Processor          => Some(ComponentInfo(ComponentType.Service, service.componentId))
      case _: scenarionode.FragmentInputDefinition  => Some(BuiltInComponentInfo.FragmentInputDefinition)
      case _: scenarionode.FragmentOutputDefinition => Some(BuiltInComponentInfo.FragmentOutputDefinition)
      case fragment: scenarionode.FragmentInput     => Some(ComponentInfo(ComponentType.Fragment, fragment.componentId))
      case _: scenarionode.FragmentUsageOutput      => None
      case _: scenarionode.BranchEndData            => None
    }
  }

}
