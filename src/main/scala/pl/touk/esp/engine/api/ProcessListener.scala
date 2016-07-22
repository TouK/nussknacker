package pl.touk.esp.engine.api

import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.engine.graph.node.Node

import scala.util.Try

trait ProcessListener {

  def nodeEntered(node: Node, context: Context): Unit

  def expressionEvaluated(expr: Expression, context: Context, result: Any): Unit

  def serviceInvoked(id: String, context: Context, result: Try[Any]): Unit

}
