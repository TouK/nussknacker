package pl.touk.process.model.api

import pl.touk.process.model.graph.expression.Expression
import pl.touk.process.model.graph.node.Node

trait ProcessListener {

  def nodeEntered(ctx: Ctx, node: Node): Unit

  def expressionEvaluated(expr: Expression, result: Any): Unit

  def serviceInvoked(id: String, result: Any): Unit

}
