package pl.touk.nussknacker.engine.component

import pl.touk.nussknacker.engine.api.component.ComponentType
import pl.touk.nussknacker.engine.api.exception.NodeComponentInfo
import pl.touk.nussknacker.engine.compiledgraph.node._

// TODO this logic should be in one place with DefaultComponentIdProvider
object ExceptionComponentInfoExtractor {

  def fromNode(node: Node): NodeComponentInfo = {
    // warning: this logic should be kept synchronized with DefaultComponentIdProvider
    node match {
      case source: Source => NodeComponentInfo(node.id, source.ref.getOrElse("source"), ComponentType.Source)
      case sink: Sink => NodeComponentInfo(node.id, sink.ref, ComponentType.Sink)
      case _: Filter => NodeComponentInfo(node.id, "filter", ComponentType.Filter)
      case _: SplitNode => NodeComponentInfo(node.id, "split", ComponentType.Split)
      case _: Switch => NodeComponentInfo(node.id, "switch", ComponentType.Switch)
      case _: VariableBuilder => NodeComponentInfo(node.id, "variable", ComponentType.Variable)
      case CustomNode(_, _) | EndingCustomNode(_) => NodeComponentInfo(node.id, "customNode", ComponentType.CustomNode)
      case enricher: Enricher => NodeComponentInfo(node.id, enricher.service.id, ComponentType.Enricher)
      case processor: Processor => NodeComponentInfo(node.id, processor.service.id, ComponentType.Processor)
      case endingProcessor: EndingProcessor => NodeComponentInfo(node.id, endingProcessor.service.id, ComponentType.Processor)
      case _: SubprocessStart => NodeComponentInfo(node.id, "input", ComponentType.FragmentInput)
      case _: SubprocessEnd => NodeComponentInfo(node.id, "output", ComponentType.FragmentOutput)
      case _: BranchEnd => NodeComponentInfo(node.id, None)
    }
  }
}
