package pl.touk.nussknacker.engine.api

import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo

import scala.util.Try

trait ProcessListener extends Lifecycle {

  def nodeEntered(nodeId: String, context: Context, processMetaData: MetaData): Unit

  def deadEndEncountered(lastNodeId: String, context: Context, processMetaData: MetaData): Unit

  def expressionEvaluated(nodeId: String, expressionId: String,
                          expression: String, context: Context, processMetaData: MetaData, result: Any): Unit

  def serviceInvoked(nodeId: String,
                     id: String,
                     context: Context,
                     processMetaData: MetaData,
                     params: Map[String, Any],
                     result: Try[Any]): Unit

  def sinkInvoked(nodeId: String,
                  ref: String,
                  context: Context,
                  processMetaData: MetaData,
                  param: Any)

  def exceptionThrown(exceptionInfo: EspExceptionInfo[_<:Throwable]) : Unit

}

trait EmptyProcessListener extends ProcessListener {
  override def nodeEntered(nodeId: String, context: Context, processMetaData: MetaData): Unit = {}

  override def deadEndEncountered(lastNodeId: String, context: Context, processMetaData: MetaData): Unit = {}

  override def expressionEvaluated(nodeId: String, expressionId: String, expression: String, context: Context, processMetaData: MetaData, result: Any): Unit = {}

  override def serviceInvoked(nodeId: String, id: String, context: Context, processMetaData: MetaData, params: Map[String, Any], result: Try[Any]): Unit = {}

  override def sinkInvoked(nodeId: String, ref: String, context: Context, processMetaData: MetaData, param: Any): Unit = {}

  override def exceptionThrown(exceptionInfo: EspExceptionInfo[_ <: Throwable]): Unit = {}
}