package pl.touk.nussknacker.engine.livedata

import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.testmode.TestInterpreterRunner

import scala.util.Try

// This class must be serializable. It means, that when deserializing, we lose the reference to it.
// The actual data is stored in the LiveDataCollectingListenerHolder, and all instances of LiveDataCollectingListener can access the data.
class LiveDataCollectingListener private[livedata] (
    processName: ProcessName,
    maxNumberOfSamples: Int,
    throughputTimeWindowInSeconds: Int,
) extends ProcessListener
    with Serializable {

  private val variableEncoder: Any => io.circe.Json = TestInterpreterRunner.testResultsVariableEncoder

  private def storage = LiveDataCollectingListenerHolder.storage(
    processName = processName,
    maxNumberOfSamples = maxNumberOfSamples,
    throughputTimeWindowInSeconds = throughputTimeWindowInSeconds,
  )

  override def nodeEntered(nodeId: String, context: Context, processMetaData: MetaData): Unit = {
    storage.updateResults(context, _.updateNodeResult(nodeId, context, variableEncoder))
  }

  override def transitionToNextNode(
      nodeId: String,
      nextNodeId: String,
      context: Context,
      processMetaData: MetaData,
  ): Unit = {
    storage.registerTransitionBetweenNodes(nodeId, Some(nextNodeId))
    storage.updateResults(context, _.updateNodeOutputResult(nodeId, Some(nextNodeId), context, variableEncoder))
  }

  override def processingFinishedInNode(
      nodeId: String,
      context: Context,
      processMetaData: MetaData,
  ): Unit = {
    storage.registerTransitionBetweenNodes(nodeId, None)
    storage.updateResults(context, _.updateNodeOutputResult(nodeId, None, context, variableEncoder))
  }

  override def endEncountered(
      nodeId: String,
      ref: String,
      context: Context,
      processMetaData: MetaData
  ): Unit = ()

  override def deadEndEncountered(
      lastNodeId: String,
      context: Context,
      processMetaData: MetaData
  ): Unit = {}

  override def expressionEvaluated(
      nodeId: String,
      expressionId: String,
      expression: String,
      context: Context,
      processMetaData: MetaData,
      result: Any
  ): Unit = {
    storage.updateResults(context, _.updateExpressionResult(nodeId, context, expressionId, result, variableEncoder))
  }

  override def serviceInvoked(
      nodeId: String,
      id: String,
      context: Context,
      processMetaData: MetaData,
      result: Try[Any]
  ): Unit = {}

  override def exceptionThrown(exceptionInfo: NuExceptionInfo): Unit = {
    storage.updateResults(exceptionInfo.context, _.updateExceptionResult(exceptionInfo, variableEncoder))
  }

  override final def close(): Unit = ()

}
