package pl.touk.process.model.graph

import pl.touk.process.model.graph.expression.Expression
import pl.touk.process.model.graph.node._
import pl.touk.process.model.graph.processor.ProcessorRef

class NodeBuilder[R <: Node] private (create: Node => R) {

  def processor(metaData: MetaData, processor: ProcessorRef): NodeBuilder[R] =
    new NodeBuilder[R](node => create(Processor(metaData, processor, node)))

  def enricher(metaData: MetaData, processor: ProcessorRef, output: String): NodeBuilder[R] =
    new NodeBuilder[R](node => create(Enricher(metaData, processor, output, node)))

  def filter(metaData: MetaData, expression: Expression, nextFalse: Option[Node] = Option.empty): NodeBuilder[R] =
    new NodeBuilder[R](node => create(Filter(metaData, expression, node, nextFalse)))

  def to(node: Node): R = {
    create(node)
  }

  def end(metaData: MetaData): R =
    create(End(metaData))

}

object NodeBuilder {

  def start(metaData: MetaData): NodeBuilder[StartNode] =
    new NodeBuilder(StartNode(metaData, _))

  def processor(metaData: MetaData, processor: ProcessorRef): NodeBuilder[Processor] =
    new NodeBuilder(Processor(metaData, processor, _))

  def enricher(metaData: MetaData, processor: ProcessorRef, output: String): NodeBuilder[Enricher] =
    new NodeBuilder(Enricher(metaData, processor, output, _))

  def filter(metaData: MetaData, expression: Expression, nextFalse: Option[Node] = Option.empty): NodeBuilder[Filter] =
    new NodeBuilder(Filter(metaData, expression, _, nextFalse))

}