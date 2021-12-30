package pl.touk.nussknacker.engine.component

import pl.touk.nussknacker.engine.api.component.ComponentType
import pl.touk.nussknacker.engine.api.exception.ExceptionComponentInfo
import pl.touk.nussknacker.engine.compiledgraph.node.{BranchEnd, CustomNode, EndingCustomNode, EndingProcessor, Enricher, Filter, Node, Processor, Sink, Source, SplitNode, SubprocessEnd, SubprocessStart, Switch, VariableBuilder}

object ExceptionComponentInfoExtractor {

  def fromNode(node: Node): ExceptionComponentInfo = {
    node match {
      case source: Source => ExceptionComponentInfo(node.id, source.ref.getOrElse("source"),ComponentType.Source)
      case sink: Sink => ExceptionComponentInfo(node.id, sink.ref, ComponentType.Sink)
      case _: Filter => ExceptionComponentInfo(node.id, "filter", ComponentType.Filter)
      case _: SplitNode => ExceptionComponentInfo(node.id, "splitNode", ComponentType.Split)
      case _: Switch => ExceptionComponentInfo(node.id, "switch", ComponentType.Switch)
      case _: VariableBuilder => ExceptionComponentInfo(node.id, "variable", ComponentType.Variable)
      case CustomNode(_,_) | EndingCustomNode(_) => ExceptionComponentInfo(node.id, "customNode", ComponentType.CustomNode)
      case enricher: Enricher => ExceptionComponentInfo(node.id, enricher.service.id, ComponentType.Enricher)
      case processor: Processor => ExceptionComponentInfo(node.id, processor.service.id, ComponentType.Processor)
      case endingProcessor: EndingProcessor => ExceptionComponentInfo(node.id, endingProcessor.service.id, ComponentType.Processor)
      case _: SubprocessStart => ExceptionComponentInfo(node.id, "input", ComponentType.FragmentInput)
      case _: SubprocessEnd => ExceptionComponentInfo(node.id, "output", ComponentType.FragmentOutput)
      case _: BranchEnd => ExceptionComponentInfo(node.id, "branchEnd", ComponentType.BranchEnd)
    }
  }

}
