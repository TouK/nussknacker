package pl.touk.esp.engine.build

import pl.touk.esp.engine.graph.expression._
import pl.touk.esp.engine.graph.node._
import pl.touk.esp.engine.graph.service.ServiceRef
import pl.touk.esp.engine.graph.variable._

class GraphBuilder[R <: Node] private(create: Node => R) {

  def buildVariable(id: String, varName: String, fields: Field*) =
    new GraphBuilder[R](node => create(VariableBuilder(id, varName, fields, node)))

  def processor(id: String, processor: ServiceRef): GraphBuilder[R] =
    new GraphBuilder[R](node => create(Processor(id, processor, node)))

  def enricher(id: String, processor: ServiceRef, output: String): GraphBuilder[R] =
    new GraphBuilder[R](node => create(Enricher(id, processor, output, node)))

  def filter(id: String, expression: Expression, nextFalse: Option[Node] = Option.empty): GraphBuilder[R] =
    new GraphBuilder[R](node => create(Filter(id, expression, node, nextFalse)))

  def to(node: Node): R = {
    create(node)
  }

  def end(id: String): R =
    create(End(id))

  def end(id: String, expression: Expression): R =
    create(End(id, Some(expression)))

}

object GraphBuilder {

  def start(id: String): GraphBuilder[StartNode] =
    new GraphBuilder(StartNode(id, _))

  def buildVariable(id: String, varName: String, fields: Field*) =
    new GraphBuilder(VariableBuilder(id, varName, fields, _))

  def processor(id: String, processor: ServiceRef): GraphBuilder[Processor] =
    new GraphBuilder(Processor(id, processor, _))

  def enricher(id: String, processor: ServiceRef, output: String): GraphBuilder[Enricher] =
    new GraphBuilder(Enricher(id, processor, output, _))

  def filter(id: String, expression: Expression, nextFalse: Option[Node] = Option.empty): GraphBuilder[Filter] =
    new GraphBuilder(Filter(id, expression, _, nextFalse))

}