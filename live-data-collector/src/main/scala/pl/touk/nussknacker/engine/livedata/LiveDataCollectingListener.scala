package pl.touk.nussknacker.engine.livedata

import io.circe.Json
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.testmode.TestInterpreterRunner

import java.time.Instant
import scala.util.Try

// This class must be serializable. It means, that when deserializing, we lose the reference to it.
// The actual data is stored in the LiveDataCollectingListenerHolder, and all instances of LiveDataCollectingListener can access the data.
class LiveDataCollectingListener private[livedata] (
    processName: ProcessName,
    maxNumberOfRecords: Int,
    throughputTimeWindowInSeconds: Int,
) extends ProcessListener
    with Serializable {

  private val variableEncoder: Any => io.circe.Json = TestInterpreterRunner.testResultsVariableEncoder

  private def storage = LiveDataCollectingListenerHolder.storage(
    processName = processName,
    maxNumberOfRecords = maxNumberOfRecords,
    throughputTimeWindowInSeconds = throughputTimeWindowInSeconds,
  )

  override def nodeEntered(
      nodeId: String,
      context: Context,
      processMetaData: MetaData,
  ): Unit = ()

  override def transitionToNextNode(
      nodeId: String,
      nextNodeId: String,
      context: Context,
      processMetaData: MetaData,
  ): Unit = {
    storage.addLiveDataSample(
      NodeTransition(nodeId, Some(nextNodeId)),
      sampleFromContext(context, Instant.now())
    )
  }

  override def processingFinishedInNode(
      nodeId: String,
      context: Context,
      processMetaData: MetaData,
  ): Unit = {
    storage.addLiveDataSample(
      NodeTransition(nodeId, None),
      sampleFromContext(context, Instant.now())
    )
  }

  override def endEncountered(
      nodeId: String,
      ref: String,
      context: Context,
      processMetaData: MetaData,
  ): Unit = ()

  override def deadEndEncountered(
      lastNodeId: String,
      context: Context,
      processMetaData: MetaData,
  ): Unit = ()

  override def expressionEvaluated(
      nodeId: String,
      expressionId: String,
      expression: String,
      context: Context,
      processMetaData: MetaData,
      result: Any,
  ): Unit = {
    storage.addExpressionEvaluation(
      NodeId(nodeId),
      InvocationResult(context.id, Instant.now(), expressionId, encode(result)),
    )
  }

  override def serviceInvoked(
      nodeId: String,
      id: String,
      context: Context,
      processMetaData: MetaData,
      result: Try[Any],
  ): Unit = {
    storage.addExternalInvocation(
      NodeId(nodeId),
      InvocationResult(context.id, Instant.now(), id, result.map(encode).getOrElse(Json.Null)),
    )
  }

  override def exceptionThrown(
      exceptionInfo: NuExceptionInfo,
  ): Unit = {
    exceptionInfo.nodeComponentInfo match {
      case Some(nodeComponentInfo) =>
        storage.addException(
          NodeId(nodeComponentInfo.nodeId),
          ExceptionResult(
            exceptionInfo.context.id,
            Instant.now(),
            encode(exceptionInfo.context.variables),
            exceptionInfo.throwable
          ),
        )
      case None =>
        ()
    }
  }

  override final def close(): Unit = ()

  private def sampleFromContext(context: Context, timestamp: Instant): LiveDataSample =
    LiveDataSample(context.id, timestamp, encode(context.variables))

  private def encode(variables: Map[String, Any]): Map[String, Json] =
    variables.map { case (k, v) => k -> encode(v) }

  private def encode(value: Any): Json = variableEncoder(value)

}
