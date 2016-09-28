package pl.touk.esp.engine.build

import pl.touk.esp.engine.graph.evaluatedparam.Parameter
import pl.touk.esp.engine.graph.expression._
import pl.touk.esp.engine.graph.node._
import pl.touk.esp.engine.graph.service.ServiceRef
import pl.touk.esp.engine.graph.sink.SinkRef
import pl.touk.esp.engine.graph.source.SourceRef
import pl.touk.esp.engine.graph.variable._
import pl.touk.esp.engine.graph.{param, service}

import scala.concurrent.duration.Duration

class GraphBuilder[R <: Node] private(create: SubsequentNode => R) {

  def buildVariable(id: String, varName: String, fields: (String, Expression)*) =
    new GraphBuilder[R](node => create(OneOutputSubsequentNode(VariableBuilder(id, varName, fields.map(Field.tupled).toList), node)))

  def processor(id: String, svcId: String, params: (String, Expression)*): GraphBuilder[R] =
    new GraphBuilder[R](node => create(OneOutputSubsequentNode(Processor(id, ServiceRef(svcId, params.map(Parameter.tupled).toList)), node)))

  def enricher(id: String, output: String, svcId: String, params: (String, Expression)*): GraphBuilder[R] =
    new GraphBuilder[R](node => create(OneOutputSubsequentNode(Enricher(id, ServiceRef(svcId, params.map(Parameter.tupled).toList), output), node)))

  def filter(id: String, expression: Expression): GraphBuilder[R] =
    new GraphBuilder[R](node => create(FilterNode(Filter(id, expression), node, None)))

  def filter(id: String, expression: Expression, nextFalse: SubsequentNode): GraphBuilder[R] =
    new GraphBuilder[R](node => create(FilterNode(Filter(id, expression), node, Some(nextFalse))))

  def aggregate(id: String, aggregatedVar: String,
                keyExpression: Expression, duration: Duration, step: Duration,
                triggerExpression: Option[Expression] = None, foldingFunRef: Option[String] = None): GraphBuilder[R] =
    new GraphBuilder[R](node => create(OneOutputSubsequentNode(Aggregate(id, aggregatedVar, keyExpression,
      duration.toMillis, step.toMillis, triggerExpression, foldingFunRef), node)))

  def sink(id: String, typ: String, params: (String, String)*): R =
    create(GraphBuilder.sink(id, typ, params: _*))

  def sink(id: String, expression: Expression, typ: String, params: (String, String)*): R =
    create(GraphBuilder.sink(id, expression, typ, params: _*))

  def processorEnd(id: String, svcId: String, params: (String, Expression)*): R =
    create(GraphBuilder.processorEnd(id, svcId, params: _*))

  def switch(id: String, expression: Expression, exprVal: String, nexts: Case*): R =
    create(GraphBuilder.switch(id, expression, exprVal, nexts: _*))

  def switch(id: String, expression: Expression, exprVal: String,
             defaultNext: SubsequentNode, nexts: Case*): R =
    create(GraphBuilder.switch(id, expression, exprVal, defaultNext, nexts: _*))

  def customNode(id: String, outputVar: String, customNodeRef: String, params: (String, Expression)*): GraphBuilder[R]  =
    new GraphBuilder[R](node => create(OneOutputSubsequentNode(CustomNode(id, outputVar, customNodeRef, params.map(Parameter.tupled).toList), node)))

  def to(node: SubsequentNode): R =
    create(node)

}

object GraphBuilder {

  def source(id: String, typ: String, params: (String, String)*): GraphBuilder[SourceNode] =
    new GraphBuilder(SourceNode(Source(id, SourceRef(typ, params.map(param.Parameter.tupled).toList)), _))

  def buildVariable(id: String, varName: String, fields: Field*): GraphBuilder[OneOutputSubsequentNode] =
    new GraphBuilder(OneOutputSubsequentNode(VariableBuilder(id, varName, fields.toList), _))

  def processor(id: String, svcId: String, params: (String, Expression)*): GraphBuilder[OneOutputSubsequentNode] =
    new GraphBuilder(OneOutputSubsequentNode(Processor(id, ServiceRef(svcId, params.map(Parameter.tupled).toList)), _))

  def enricher(id: String, output: String, svcId: String, params: (String, Expression)*): GraphBuilder[OneOutputSubsequentNode] =
    new GraphBuilder(OneOutputSubsequentNode(Enricher(id, ServiceRef(svcId, params.map(Parameter.tupled).toList), output), _))

  def filter(id: String, expression: Expression): GraphBuilder[FilterNode] =
    new GraphBuilder(FilterNode(Filter(id, expression), _, None))

  def filter(id: String, expression: Expression, nextFalse: SubsequentNode): GraphBuilder[FilterNode] =
    new GraphBuilder(FilterNode(Filter(id, expression), _, Some(nextFalse)))

  def customNode(id: String, outputVar: String, customNodeRef: String, params: (String, Expression)*) : GraphBuilder[OneOutputSubsequentNode] =
    new GraphBuilder(OneOutputSubsequentNode(CustomNode(id, outputVar, customNodeRef, params.map(Parameter.tupled).toList), _))

  def aggregate(id: String, aggregatedVar: String,
                keyExpression: Expression, duration: Duration, step: Duration,
                triggerExpression: Option[Expression] = None, foldingFunRef: Option[String] = None): GraphBuilder[OneOutputSubsequentNode] =
    new GraphBuilder(OneOutputSubsequentNode(Aggregate(id, aggregatedVar, keyExpression,
      duration.toMillis, step.toMillis, triggerExpression, foldingFunRef), _))

  def sink(id: String, typ: String, params: (String, String)*): EndingNode =
    EndingNode(Sink(id, SinkRef(typ, params.map(param.Parameter.tupled).toList)))

  def sink(id: String, expression: Expression, typ: String, params: (String, String)*): EndingNode =
    EndingNode(Sink(id, SinkRef(typ, params.map(param.Parameter.tupled).toList), Some(expression)))

  def processorEnd(id: String, svcId: String, params: (String, Expression)*): EndingNode =
    EndingNode(Processor(id, ServiceRef(svcId, params.map(Parameter.tupled).toList)))

  def switch(id: String, expression: Expression, exprVal: String, nexts: Case*): SwitchNode =
    SwitchNode(Switch(id, expression, exprVal), nexts.toList, None)

  def switch(id: String, expression: Expression, exprVal: String,
             defaultNext: SubsequentNode, nexts: Case*): SwitchNode =
    SwitchNode(Switch(id, expression, exprVal), nexts.toList, Some(defaultNext))

}