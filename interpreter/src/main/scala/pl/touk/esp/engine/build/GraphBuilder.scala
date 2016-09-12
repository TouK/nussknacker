package pl.touk.esp.engine.build

import pl.touk.esp.engine.graph.expression._
import pl.touk.esp.engine.graph.node._
import pl.touk.esp.engine.graph.service.ServiceRef
import pl.touk.esp.engine.graph.sink.SinkRef
import pl.touk.esp.engine.graph.source.SourceRef
import pl.touk.esp.engine.graph.variable._
import pl.touk.esp.engine.graph.{param, service}

import scala.concurrent.duration.Duration

class GraphBuilder[R <: Node] private(create: Node => R) {

  def buildVariable(id: String, varName: String, fields: (String, Expression)*) =
    new GraphBuilder[R](node => create(VariableBuilder(id, varName, fields.map(Field.tupled).toList, node)))

  def processor(id: String, svcId: String, params: (String, Expression)*): GraphBuilder[R] =
    new GraphBuilder[R](node => create(Processor(id, ServiceRef(svcId, params.map(service.Parameter.tupled).toList), node)))

  def enricher(id: String, output: String, svcId: String, params: (String, Expression)*): GraphBuilder[R] =
    new GraphBuilder[R](node => create(Enricher(id, ServiceRef(svcId, params.map(service.Parameter.tupled).toList), output, node)))

  def filter(id: String, expression: Expression, nextFalse: Option[Node] = Option.empty): GraphBuilder[R] =
    new GraphBuilder[R](node => create(Filter(id, expression, node, nextFalse)))

  def aggregate(id: String, aggregatedVar: String,
                keyExpression: Expression, duration: Duration, step: Duration,
                triggerExpression: Option[Expression] = None, foldingFunRef: Option[String] = None): GraphBuilder[R] =
    new GraphBuilder[R](node => create(Aggregate(id, aggregatedVar, keyExpression,
      duration.toMillis, step.toMillis, triggerExpression, foldingFunRef, node)))

  def sink(id: String, typ: String, params: (String, String)*): R =
    create(GraphBuilder.sink(id, typ, params: _*))

  def sink(id: String, expression: Expression, typ: String, params: (String, String)*): R =
    create(GraphBuilder.sink(id, expression, typ, params: _*))

  def processorEnd(id: String, svcId: String, params: (String, Expression)*): R =
    create(GraphBuilder.processorEnd(id, svcId, params: _*))

  def switch(id: String, expression: Expression, exprVal: String, nexts: Case*): R =
    create(GraphBuilder.switch(id, expression, exprVal, nexts: _*))

  def switch(id: String, expression: Expression, exprVal: String,
             defaultNext: Node, nexts: Case*): R =
    create(GraphBuilder.switch(id, expression, exprVal, defaultNext, nexts: _*))

  def to(node: Node): R =
    create(node)

}

object GraphBuilder {

  def source(id: String, typ: String, params: (String, String)*): GraphBuilder[Source] =
    new GraphBuilder(Source(id, SourceRef(typ, params.map(param.Parameter.tupled).toList), _))

  def buildVariable(id: String, varName: String, fields: Field*) =
    new GraphBuilder(VariableBuilder(id, varName, fields.toList, _))

  def processor(id: String, svcId: String, params: (String, Expression)*): GraphBuilder[Processor] =
    new GraphBuilder(Processor(id, ServiceRef(svcId, params.map(service.Parameter.tupled).toList), _))

  def enricher(id: String, output: String, svcId: String, params: (String, Expression)*): GraphBuilder[Enricher] =
    new GraphBuilder(Enricher(id, ServiceRef(svcId, params.map(service.Parameter.tupled).toList), output, _))

  def filter(id: String, expression: Expression, nextFalse: Option[Node] = Option.empty): GraphBuilder[Filter] =
    new GraphBuilder(Filter(id, expression, _, nextFalse))

  def aggregate(id: String, aggregatedVar: String,
                keyExpression: Expression, duration: Duration, step: Duration,
                triggerExpression: Option[Expression] = None, foldingFunRef: Option[String] = None): GraphBuilder[Aggregate] =
    new GraphBuilder(Aggregate(id, aggregatedVar, keyExpression,
      duration.toMillis, step.toMillis, triggerExpression, foldingFunRef, _))

  def sink(id: String, typ: String, params: (String, String)*): Sink =
    Sink(id, SinkRef(typ, params.map(param.Parameter.tupled).toList))

  def sink(id: String, expression: Expression, typ: String, params: (String, String)*): Sink =
    Sink(id, SinkRef(typ, params.map(param.Parameter.tupled).toList), Some(expression))

  def processorEnd(id: String, svcId: String, params: (String, Expression)*): EndingProcessor =
    EndingProcessor(id, ServiceRef(svcId, params.map(service.Parameter.tupled).toList))

  def switch(id: String, expression: Expression, exprVal: String, nexts: Case*): Switch =
    Switch(id, expression, exprVal, nexts.toList, None)

  def switch(id: String, expression: Expression, exprVal: String,
             defaultNext: Node, nexts: Case*): Switch =
    Switch(id, expression, exprVal, nexts.toList, Some(defaultNext))

}