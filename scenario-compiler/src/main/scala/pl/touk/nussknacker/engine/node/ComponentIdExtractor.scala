package pl.touk.nussknacker.engine.node

import pl.touk.nussknacker.engine.api.component.{BuiltInComponentId, ComponentId, ComponentType}
import pl.touk.nussknacker.engine.compiledgraph.{node => compilednode}
import pl.touk.nussknacker.engine.graph.{node => scenarionode}

object ComponentIdExtractor {

  def fromCompiledNode(node: compilednode.Node): Option[ComponentId] = {
    node match {
      case compilednode.Source(_, Some(ref), _)            => Some(ComponentId(ComponentType.Source, ref))
      case compilednode.Source(_, None, _)                 => None
      case sink: compilednode.Sink                         => Some(ComponentId(ComponentType.Sink, sink.ref))
      case _: compilednode.Filter                          => Some(BuiltInComponentId.Filter)
      case _: compilednode.SplitNode                       => Some(BuiltInComponentId.Split)
      case _: compilednode.Switch                          => Some(BuiltInComponentId.Choice)
      case compilednode.VariableBuilder(_, _, Left(_), _)  => Some(BuiltInComponentId.Variable)
      case compilednode.VariableBuilder(_, _, Right(_), _) => Some(BuiltInComponentId.RecordVariable)
      case customNode: compilednode.CustomNode => Some(ComponentId(ComponentType.CustomComponent, customNode.ref))
      case customNode: compilednode.EndingCustomNode =>
        Some(ComponentId(ComponentType.CustomComponent, customNode.ref))
      case service: compilednode.Enricher        => Some(ComponentId(ComponentType.Service, service.service.id))
      case service: compilednode.Processor       => Some(ComponentId(ComponentType.Service, service.service.id))
      case service: compilednode.EndingProcessor => Some(ComponentId(ComponentType.Service, service.service.id))
      case _: compilednode.FragmentOutput        => None
      case _: compilednode.FragmentUsageStart    => None
      case _: compilednode.FragmentUsageEnd      => None
      case _: compilednode.BranchEnd             => None
    }
  }

  def fromScenarioNode(node: scenarionode.NodeData): Option[ComponentId] = {
    node match {
      case source: scenarionode.Source     => Some(ComponentId(ComponentType.Source, source.componentId))
      case sink: scenarionode.Sink         => Some(ComponentId(ComponentType.Sink, sink.componentId))
      case _: scenarionode.Filter          => Some(BuiltInComponentId.Filter)
      case _: scenarionode.Split           => Some(BuiltInComponentId.Split)
      case _: scenarionode.Switch          => Some(BuiltInComponentId.Choice)
      case _: scenarionode.Variable        => Some(BuiltInComponentId.Variable)
      case _: scenarionode.VariableBuilder => Some(BuiltInComponentId.RecordVariable)
      case custom: scenarionode.CustomNodeData =>
        Some(ComponentId(ComponentType.CustomComponent, custom.componentId))
      case service: scenarionode.Enricher           => Some(ComponentId(ComponentType.Service, service.componentId))
      case service: scenarionode.Processor          => Some(ComponentId(ComponentType.Service, service.componentId))
      case _: scenarionode.FragmentInputDefinition  => Some(BuiltInComponentId.FragmentInputDefinition)
      case _: scenarionode.FragmentOutputDefinition => Some(BuiltInComponentId.FragmentOutputDefinition)
      case fragment: scenarionode.FragmentInput     => Some(ComponentId(ComponentType.Fragment, fragment.componentId))
      case _: scenarionode.FragmentUsageOutput      => None
      case _: scenarionode.BranchEndData            => None
    }
  }

}
