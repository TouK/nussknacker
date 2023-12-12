package pl.touk.nussknacker.engine.api

import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo

import scala.util.Try

trait ProcessListener extends Lifecycle {

  def nodeEntered(nodeId: String, context: ScenarioProcessingContext, processMetaData: MetaData): Unit

  def endEncountered(nodeId: String, ref: String, context: ScenarioProcessingContext, processMetaData: MetaData): Unit

  def deadEndEncountered(lastNodeId: String, context: ScenarioProcessingContext, processMetaData: MetaData): Unit

  def expressionEvaluated(
      nodeId: String,
      expressionId: String,
      expression: String,
      context: ScenarioProcessingContext,
      processMetaData: MetaData,
      result: Any
  ): Unit

  def serviceInvoked(
      nodeId: String,
      id: String,
      context: ScenarioProcessingContext,
      processMetaData: MetaData,
      params: Map[String, Any],
      result: Try[Any]
  ): Unit

  def exceptionThrown(exceptionInfo: NuExceptionInfo[_ <: Throwable]): Unit

}

trait EmptyProcessListener extends ProcessListener {
  override def nodeEntered(nodeId: String, context: ScenarioProcessingContext, processMetaData: MetaData): Unit = {}

  override def endEncountered(
      nodeId: String,
      ref: String,
      context: ScenarioProcessingContext,
      processMetaData: MetaData
  ): Unit = {}

  override def deadEndEncountered(
      lastNodeId: String,
      context: ScenarioProcessingContext,
      processMetaData: MetaData
  ): Unit = {}

  override def expressionEvaluated(
      nodeId: String,
      expressionId: String,
      expression: String,
      context: ScenarioProcessingContext,
      processMetaData: MetaData,
      result: Any
  ): Unit = {}

  override def serviceInvoked(
      nodeId: String,
      id: String,
      context: ScenarioProcessingContext,
      processMetaData: MetaData,
      params: Map[String, Any],
      result: Try[Any]
  ): Unit = {}

  override def exceptionThrown(exceptionInfo: NuExceptionInfo[_ <: Throwable]): Unit = {}
}
