package pl.touk.esp.engine.build

import pl.touk.esp.engine._
import pl.touk.esp.engine.graph.sink.SinkRef
import pl.touk.esp.engine.graph.expression._
import pl.touk.esp.engine.graph.node._
import pl.touk.esp.engine.graph.service.ServiceRef
import pl.touk.esp.engine.graph.source.SourceRef
import pl.touk.esp.engine.graph.variable._

import scala.concurrent.duration.Duration

class GraphBuilder[R <: Node] private(create: Node => R) {

  def buildVariable(id: String, varName: String, fields: Field*) =
    new GraphBuilder[R](node => create(VariableBuilder(id, varName, fields.toList, node)))

  def processor(id: String, service: ServiceRef): GraphBuilder[R] =
    new GraphBuilder[R](node => create(Processor(id, service, node)))

  def enricher(id: String, service: ServiceRef, output: String): GraphBuilder[R] =
    new GraphBuilder[R](node => create(Enricher(id, service, output, node)))

  def filter(id: String, expression: Expression, nextFalse: Option[Node] = Option.empty): GraphBuilder[R] =
    new GraphBuilder[R](node => create(Filter(id, expression, node, nextFalse)))

  def to(node: Node): R = {
    create(node)
  }

  def aggregate(id: String, aggregatedVar: String, expression: Expression, duration: Duration, step: Duration) : GraphBuilder[R]
   = new GraphBuilder[R](node => create(Aggregate(id, aggregatedVar, expression, duration.toMillis, step.toMillis, node)))

  def sink(id: String, typ: String, params: graph.sink.Parameter*): R =
    create(Sink(id, SinkRef(typ, params.toList)))

  def sink(id: String, expression: Expression, typ: String, params: graph.sink.Parameter*): R =
    create(Sink(id, SinkRef(typ, params.toList), Some(expression)))

}

object GraphBuilder {

  def source(id: String, typ: String, params: graph.source.Parameter*): GraphBuilder[Source] =
    new GraphBuilder(Source(id, SourceRef(typ, params.toList), _))

  def buildVariable(id: String, varName: String, fields: Field*) =
    new GraphBuilder(VariableBuilder(id, varName, fields.toList, _))

  def processor(id: String, service: ServiceRef): GraphBuilder[Processor] =
    new GraphBuilder(Processor(id, service, _))

  def enricher(id: String, service: ServiceRef, output: String): GraphBuilder[Enricher] =
    new GraphBuilder(Enricher(id, service, output, _))

  def filter(id: String, expression: Expression, nextFalse: Option[Node] = Option.empty): GraphBuilder[Filter] =
    new GraphBuilder(Filter(id, expression, _, nextFalse))

}