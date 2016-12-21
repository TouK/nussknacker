package pl.touk.esp.engine.api

import scala.util.Try

trait ProcessListener {

  def nodeEntered(nodeId: String, context: Context, processMetaData: MetaData, mode: InterpreterMode): Unit

  def deadEndEncountered(lastNodeId: String, context: Context, processMetaData: MetaData): Unit

  def expressionEvaluated(nodeId: String, expressionId: String,
                          expression: String, context: Context, processMetaData: MetaData, result: Any): Unit

  def serviceInvoked(nodeId: String, id: String,
                     context: Context,
                     processMetaData: MetaData,
                     params: Map[String, Any],
                     result: Try[Any]): Unit

  def sinkInvoked(nodeId: String, id: String,
                       context: Context,
                       processMetaData: MetaData,
                       param: Any)

}