package pl.touk.esp.engine.util

import org.slf4j.LoggerFactory
import pl.touk.esp.engine.api.{Context, ProcessListener}
import pl.touk.esp.engine.graph.expression.Expression
import pl.touk.esp.engine.graph.node.Node

import scala.util.Try

class LoggingListener extends ProcessListener {

  private lazy val log = LoggerFactory.getLogger(getClass)

  override def nodeEntered(node: Node, context: Context): Unit = {
    log.debug(s"Node: ${node.id} entered")
  }

  override def serviceInvoked(id: String, context: Context, result: Try[Any]): Unit = {
    log.debug(s"Service: $id invocation ended-up with result: $result")
  }

  override def expressionEvaluated(expr: Expression, context: Context, result: Any): Unit = {
    log.debug(s"Expression: $expr evaluated with result: $result")
  }

}