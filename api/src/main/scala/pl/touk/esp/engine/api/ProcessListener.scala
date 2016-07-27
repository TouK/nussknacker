package pl.touk.esp.engine.api

import scala.util.Try

trait ProcessListener {

  def nodeEntered(nodeId: String, context: Context): Unit

  def expressionEvaluated(expression: String, context: Context, result: Any): Unit

  def serviceInvoked(id: String, context: Context, result: Try[Any]): Unit

}