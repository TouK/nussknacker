package pl.touk.nussknacker.engine.api

import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo

import scala.util.Try

trait ProcessListener extends Lifecycle {

  def nodeEntered(nodeId: NodeId, context: Context, processMetaData: MetaData): Unit

  def transitionToNextNode(nodeId: NodeId, nextNodeId: NodeId, context: Context, processMetaData: MetaData): Unit

  def transitionFromFragmentStartToNodeAfterFragment(
      nodeId: NodeId,
      nextNodeId: NodeId,
      context: Context,
      processMetaData: MetaData
  ): Unit

  def processingFinishedInNode(nodeId: NodeId, context: Context, processMetaData: MetaData): Unit

  def endEncountered(nodeId: NodeId, ref: String, context: Context, processMetaData: MetaData): Unit

  def deadEndEncountered(lastNodeId: NodeId, context: Context, processMetaData: MetaData): Unit

  def expressionEvaluated(
      nodeId: NodeId,
      expressionId: String,
      expression: String,
      context: Context,
      processMetaData: MetaData,
      result: Any
  ): Unit

  def serviceInvoked(
      nodeId: NodeId,
      id: String,
      context: Context,
      processMetaData: MetaData,
      result: Try[Any]
  ): Unit

  def exceptionThrown(exceptionInfo: NuExceptionInfo): Unit

}

trait EmptyProcessListener extends ProcessListener {
  override def nodeEntered(nodeId: NodeId, context: Context, processMetaData: MetaData): Unit = ()

  override def transitionToNextNode(
      nodeId: NodeId,
      nextNodeId: NodeId,
      context: Context,
      processMetaData: MetaData,
  ): Unit = ()

  override def transitionFromFragmentStartToNodeAfterFragment(
      nodeId: NodeId,
      nextNodeId: NodeId,
      context: Context,
      processMetaData: MetaData
  ): Unit = ()

  override def processingFinishedInNode(
      nodeId: NodeId,
      context: Context,
      processMetaData: MetaData,
  ): Unit = ()

  override def endEncountered(
      nodeId: NodeId,
      ref: String,
      context: Context,
      processMetaData: MetaData
  ): Unit = {}

  override def deadEndEncountered(
      lastNodeId: NodeId,
      context: Context,
      processMetaData: MetaData
  ): Unit = {}

  override def expressionEvaluated(
      nodeId: NodeId,
      expressionId: String,
      expression: String,
      context: Context,
      processMetaData: MetaData,
      result: Any
  ): Unit = {}

  override def serviceInvoked(
      nodeId: NodeId,
      id: String,
      context: Context,
      processMetaData: MetaData,
      result: Try[Any]
  ): Unit = {}

  override def exceptionThrown(exceptionInfo: NuExceptionInfo): Unit = {}
}
