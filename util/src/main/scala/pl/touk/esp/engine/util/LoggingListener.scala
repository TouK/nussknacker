package pl.touk.esp.engine.util

import com.typesafe.scalalogging.LazyLogging
import pl.touk.esp.engine.api.{Context, InterpreterMode, MetaData, ProcessListener}

import scala.util.Try

object LoggingListener extends ProcessListener with LazyLogging {

  override def nodeEntered(nodeId: String, context: Context, processMetaData: MetaData, mode: InterpreterMode): Unit = {
    lazy val modeSuffix = if (mode == InterpreterMode.Traverse) "" else " for " + mode
    logger.debug(s"${processMetaData.id}: Node: $nodeId entered$modeSuffix")
  }

  override def deadEndEncountered(context: Context, processMetaData: MetaData): Unit = {
    logger.debug(s"${processMetaData.id}: Dead end encountered")
  }

  override def serviceInvoked(id: String, context: Context, processMetaData: MetaData, result: Try[Any]): Unit = {
    logger.debug(s"${processMetaData.id}: Service: $id invocation ended-up with result: $result")
  }

  override def expressionEvaluated(expr: String, context: Context, processMetaData: MetaData, result: Any): Unit = {
    logger.debug(s"${processMetaData.id}: Expression: $expr evaluated with result: $result")
  }

}