package pl.touk.process.model.util

import org.slf4j.LoggerFactory
import pl.touk.process.model.api.{Ctx, ProcessListener}
import pl.touk.process.model.graph.expression.Expression
import pl.touk.process.model.graph.node.Node

class LoggingListener extends ProcessListener {

  private lazy val log = LoggerFactory.getLogger(getClass)

  override def nodeEntered(ctx: Ctx, node: Node): Unit = {
    log.debug(s"Node: ${node.metaData.id} entered")
  }

  override def serviceInvoked(id: String, result: Any): Unit = {
    log.debug(s"Service: $id invocation ended-up with result: $result")
  }

  override def expressionEvaluated(expr: Expression, result: Any): Unit = {
    log.debug(s"Expression: $expr evaluated with result: $result")
  }

}
