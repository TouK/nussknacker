package pl.touk.esp.engine.util

import org.slf4j.LoggerFactory
import pl.touk.esp.engine.api.{Context, InterpreterMode, ProcessListener}

import scala.util.Try

class LoggingListener extends ProcessListener {

  private lazy val log = LoggerFactory.getLogger(getClass)

  override def nodeEntered(nodeId: String, context: Context, mode: InterpreterMode): Unit = {
    val modeSuffix = if (mode == InterpreterMode.Traverse) "" else " for " + mode
    log.debug(s"${context.processMetaData.id}: Node: $nodeId entered$modeSuffix")
  }

  override def serviceInvoked(id: String, context: Context, result: Try[Any]): Unit = {
    log.debug(s"${context.processMetaData.id}: Service: $id invocation ended-up with result: $result")
  }

  override def expressionEvaluated(expr: String, context: Context, result: Any): Unit = {
    log.debug(s"${context.processMetaData.id}: Expression: $expr evaluated with result: $result")
  }

}