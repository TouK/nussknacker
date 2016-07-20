package pl.touk.process.model.graph

import pl.touk.process.model.graph.expression.Expression
import pl.touk.process.model.graph.node._
import pl.touk.process.model.graph.processor.ProcessorRef

class ProcessBuilder[R <: Node] private(create: Node => R) {

  def processor(metaData: MetaData, processor: ProcessorRef): ProcessBuilder[R] =
    new ProcessBuilder[R](node => create(Processor(metaData, processor, node)))

  def enricher(metaData: MetaData, processor: ProcessorRef, output: String): ProcessBuilder[R] =
    new ProcessBuilder[R](node => create(Enricher(metaData, processor, output, node)))

  def filter(metaData: MetaData, expression: Expression, nextFalse: Option[Node] = Option.empty): ProcessBuilder[R] =
    new ProcessBuilder[R](node => create(Filter(metaData, expression, node, nextFalse)))

  def to(node: Node): R = {
    create(node)
  }

  def end(metaData: MetaData): R =
    create(End(metaData))

  def end(metaData: MetaData, expression: Expression): R =
    create(End(metaData, Some(expression)))
}

object ProcessBuilder {

  def start(metaData: MetaData): ProcessBuilder[StartNode] =
    new ProcessBuilder(StartNode(metaData, _))

  def processor(metaData: MetaData, processor: ProcessorRef): ProcessBuilder[Processor] =
    new ProcessBuilder(Processor(metaData, processor, _))

  def enricher(metaData: MetaData, processor: ProcessorRef, output: String): ProcessBuilder[Enricher] =
    new ProcessBuilder(Enricher(metaData, processor, output, _))

  def filter(metaData: MetaData, expression: Expression, nextFalse: Option[Node] = Option.empty): ProcessBuilder[Filter] =
    new ProcessBuilder(Filter(metaData, expression, _, nextFalse))

}