package pl.touk.esp.engine.process

import pl.touk.esp.engine.api.MetaData
import pl.touk.esp.engine.api.sink.SinkRef
import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.engine.graph.node.{Aggregate, Node, Sink}
import pl.touk.esp.engine.traverse.NodesCollector

object SplittedProcess {

  def apply(processMetadata: MetaData, node: Node): SplittedProcess =
    SplittedProcess(processMetadata, node, NodesCollector.collectNodes(node).collect {
      case Aggregate(id, aggregatedVar, keyExpr, duration, slide, next) =>
        AggregatePart(id, aggregatedVar, keyExpr, duration, slide, SplittedProcess(processMetadata, next))
      case Sink(id, ref, _) => SinkPart(id, ref)
    }.toSet)

}

case class SplittedProcess(processMetadata: MetaData, node: Node, nextParts: Set[ProcessPart])

sealed trait ProcessPart {
  def id: String
}

case class AggregatePart(id: String, aggregatedVar: String,
                         keyExpression: Expression, durationInMillis: Long, slideInMillis: Long,
                         next: SplittedProcess) extends ProcessPart

case class SinkPart(id: String, sink: SinkRef) extends ProcessPart