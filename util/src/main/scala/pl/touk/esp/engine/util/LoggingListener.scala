package pl.touk.esp.engine.util

import pl.touk.esp.engine.api.{Context, InterpreterMode, MetaData, ProcessListener}

import scala.util.Try

object LoggingListener extends ProcessListener with Serializable {
  import org.slf4j.LoggerFactory

  val className = getClass.getName.init

  //nie cachujemy bo logback sam juz to robi
  private def getLogger(keys: List[String]) = {
    val loggerKey = keys.mkString(".")
    LoggerFactory.getLogger(s"$className.$loggerKey")
  }

  override def nodeEntered(nodeId: String, context: Context, metadata: MetaData, mode: InterpreterMode): Unit = {
    lazy val modeSuffix = if (mode == InterpreterMode.Traverse) "" else " for " + mode
    getLogger(List(metadata.id, nodeId)).debug(s"Node entered$modeSuffix. Context: $context")
  }

  override def deadEndEncountered(lastNodeId: String, context: Context, metadata: MetaData): Unit = {
    getLogger(List(metadata.id, lastNodeId, "deadEnd")).debug(s"Dead end encountered. Context: $context")
  }

  override def expressionEvaluated(nodeId: String, expr: String, context: Context, metadata: MetaData, result: Any): Unit = {
    getLogger(List(metadata.id, nodeId, "expression")).debug(s"invoked expression: $expr with result $result. Context: $context")
  }

  override def serviceInvoked(nodeId: String, id: String, context: Context, metadata: MetaData, result: Try[Any]): Unit = {
    getLogger(List(metadata.id, nodeId, "service", id)).debug(s"Invocation ended-up with result: $result. Context: $context")
  }

}