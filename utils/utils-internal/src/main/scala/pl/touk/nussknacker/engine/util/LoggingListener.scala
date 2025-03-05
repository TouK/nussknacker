package pl.touk.nussknacker.engine.util

import org.slf4j.Logger
import pl.touk.nussknacker.engine.api.{Context, MetaData, ProcessListener}
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo

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

  private def debug(keys: List[String], message: => String, throwable: => Option[Throwable] = None): Unit = {
    val logger = loggerMap.computeIfAbsent(
      keys,
      (ks: List[String]) => {
        val loggerKey = ks.mkString(".")
        LoggerFactory.getLogger(s"$className.$loggerKey")
      }
    )
    if (logger.isDebugEnabled()) {
      throwable match {
        case None    => logger.debug(message)
        case Some(t) => logger.debug(message, t)
      }

    }
  }

  override def nodeEntered(nodeId: String, context: Context, metadata: MetaData): Unit = {
    debug(List(metadata.name.value, nodeId), s"Node entered. Context: $context")
  }

  override def endEncountered(
      nodeId: String,
      ref: String,
      context: Context,
      metadata: MetaData
  ): Unit = {
    debug(List(metadata.name.value, nodeId, "end", ref), s"End encountered. Context: $context")
  }

  override def deadEndEncountered(lastNodeId: String, context: Context, metadata: MetaData): Unit = {
    debug(List(metadata.name.value, lastNodeId, "deadEnd"), s"Dead end encountered. Context: $context")
  }

  override def expressionEvaluated(
      nodeId: String,
      expressionId: String,
      expr: String,
      context: Context,
      metadata: MetaData,
      result: Any
  ): Unit = {
    debug(
      List(metadata.name.value, nodeId, "expression"),
      s"invoked expression: $expr with result $result. Context: $context"
    )
  }

  override def serviceInvoked(
      nodeId: String,
      id: String,
      context: Context,
      metadata: MetaData,
      result: Try[Any]
  ): Unit = {
    debug(
      List(metadata.name.value, nodeId, "service", id),
      s"Invocation ended-up with result: $result. Context: $context"
    )
  }

  override def exceptionThrown(exceptionInfo: NuExceptionInfo[_ <: Throwable]): Unit = {
    debug(
      List(exceptionInfo.context.id, "exception"),
      "Exception occurred",
      Some(exceptionInfo.throwable)
    )
  }

}
