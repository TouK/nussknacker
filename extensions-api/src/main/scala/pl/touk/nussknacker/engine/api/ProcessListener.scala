package pl.touk.nussknacker.engine.api

import pl.touk.nussknacker.engine.api.ProcessListener.Transition
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo

import scala.util.Try

trait ProcessListener extends Lifecycle {

  def nodeEntered(nodeId: NodeId, context: Context, processMetaData: MetaData): Unit

  def transitionToNextNode(transition: Transition, context: Context, processMetaData: MetaData): Unit

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

object ProcessListener {
  sealed trait Transition

  object Transition {

    final case class DirectTransition(
        nodeId: NodeId,
        nextNodeId: NodeId
    ) extends Transition

    final case class TransitionFromFragmentStartToNodeAfterFragment(
        fragmentStartNodeId: NodeId,
        afterFragmentNodeId: NodeId
    ) extends Transition

  }

}

trait EmptyProcessListener extends ProcessListener {
  override def nodeEntered(nodeId: NodeId, context: Context, processMetaData: MetaData): Unit = ()

  override def transitionToNextNode(
      transition: Transition,
      context: Context,
      processMetaData: MetaData,
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
