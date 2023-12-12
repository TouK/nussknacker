package pl.touk.nussknacker.engine.util

import org.slf4j.Logger
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.{MetaData, ProcessListener, ScenarioProcessingContext}

import java.util.concurrent.ConcurrentHashMap
import scala.util.Try

object LoggingListener extends ProcessListener with Serializable {
  import org.slf4j.LoggerFactory

  val className: String = getClass.getName.init

  /*
    Flink >= 1.11 uses log4j2 as default logging framework. In JDK11 dynamic logger loading can degrade performance:
    https://issues.apache.org/jira/browse/LOG4J2-2537
    What's more, under heavy load string concatenation in creating logger name is also no longer negligible...
   */
  private val loggerMap = new ConcurrentHashMap[List[String], Logger]()

  private def debug(keys: List[String], message: => String): Unit = {
    val logger = loggerMap.computeIfAbsent(
      keys,
      (ks: List[String]) => {
        val loggerKey = ks.mkString(".")
        LoggerFactory.getLogger(s"$className.$loggerKey")
      }
    )
    if (logger.isDebugEnabled()) {
      logger.debug(message)
    }
  }

  override def nodeEntered(nodeId: String, context: ScenarioProcessingContext, metadata: MetaData): Unit = {
    debug(List(metadata.id, nodeId), s"Node entered. Context: $context")
  }

  override def endEncountered(
      nodeId: String,
      ref: String,
      context: ScenarioProcessingContext,
      metadata: MetaData
  ): Unit = {
    debug(List(metadata.id, nodeId, "end", ref), s"End encountered. Context: $context")
  }

  override def deadEndEncountered(lastNodeId: String, context: ScenarioProcessingContext, metadata: MetaData): Unit = {
    debug(List(metadata.id, lastNodeId, "deadEnd"), s"Dead end encountered. Context: $context")
  }

  override def expressionEvaluated(
      nodeId: String,
      expressionId: String,
      expr: String,
      context: ScenarioProcessingContext,
      metadata: MetaData,
      result: Any
  ): Unit = {
    debug(List(metadata.id, nodeId, "expression"), s"invoked expression: $expr with result $result. Context: $context")
  }

  override def serviceInvoked(
      nodeId: String,
      id: String,
      context: ScenarioProcessingContext,
      metadata: MetaData,
      params: Map[String, Any],
      result: Try[Any]
  ): Unit = {
    debug(List(metadata.id, nodeId, "service", id), s"Invocation ended-up with result: $result. Context: $context")
  }

  override def exceptionThrown(exceptionInfo: NuExceptionInfo[_ <: Throwable]): Unit = {
    // TODO:??
  }

}
