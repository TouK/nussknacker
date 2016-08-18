package pl.touk.esp.engine.util

import com.typesafe.scalalogging.LazyLogging
import pl.touk.esp.engine.api.{Context, InterpreterMode, ProcessListener}

import scala.util.Try

object LoggingListener extends ProcessListener with LazyLogging {

  override def nodeEntered(nodeId: String, context: Context, mode: InterpreterMode): Unit = {
    lazy val modeSuffix = if (mode == InterpreterMode.Traverse) "" else " for " + mode
    logger.debug(s"${context.processMetaData.id}: Node: $nodeId entered$modeSuffix")
  }

  override def serviceInvoked(id: String, context: Context, result: Try[Any]): Unit = {
    logger.debug(s"${context.processMetaData.id}: Service: $id invocation ended-up with result: $result")
  }

  override def expressionEvaluated(expr: String, context: Context, result: Any): Unit = {
    logger.debug(s"${context.processMetaData.id}: Expression: $expr evaluated with result: $result")
  }

}