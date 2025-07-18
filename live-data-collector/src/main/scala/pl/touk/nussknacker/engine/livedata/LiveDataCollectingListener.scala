package pl.touk.nussknacker.engine.livedata

import io.circe.Json
import pl.touk.nussknacker.engine.ModelConfig.LiveDataPreviewMode
import pl.touk.nussknacker.engine.ModelConfig.LiveDataPreviewMode.LiveDataStorage
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.livedata.LiveDataUploader.LiveDataUploaderConfig
import pl.touk.nussknacker.engine.newdeployment.DeploymentId
import pl.touk.nussknacker.engine.testmode.TestInterpreterRunner

import java.time.Instant
import scala.util.Try

// This class must be serializable. It means, that when deserializing, we lose the reference to it.
// The actual data is stored in the LiveDataCollectingListenerHolder, and all instances of LiveDataCollectingListener can access the data.
class LiveDataCollectingListener private[livedata] (
    processIdWithName: ProcessIdWithName,
    deploymentId: Option[DeploymentId],
    uploaderConfig: Option[LiveDataUploaderConfig],
    maxNumberOfRecords: Int,
    throughputTimeWindowInSeconds: Int,
) extends ProcessListener
    with Serializable {

  private val variableEncoder: Any => io.circe.Json = TestInterpreterRunner.testResultsVariableEncoder

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
  ): Unit = performStorageOperation {
    _.addLiveDataSample(
      NodeTransition(nodeId, Some(nextNodeId)),
      sampleFromContext(context, Instant.now())
    )
  }

  override def processingFinishedInNode(
      nodeId: String,
      context: Context,
      processMetaData: MetaData,
  ): Unit = performStorageOperation {
    _.addLiveDataSample(
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
  ): Unit = performStorageOperation {
    _.addExpressionEvaluation(
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
  ): Unit = performStorageOperation {
    _.addExternalInvocation(
      NodeId(nodeId),
      InvocationResult(context.id, Instant.now(), id, result.map(encode).getOrElse(Json.Null)),
    )
  }

  override def exceptionThrown(
      exceptionInfo: NuExceptionInfo,
  ): Unit = {
    exceptionInfo.nodeComponentInfo match {
      case Some(nodeComponentInfo) =>
        performStorageOperation {
          _.addException(
            NodeId(nodeComponentInfo.nodeId),
            ExceptionResult(
              exceptionInfo.context.id,
              Instant.now(),
              encode(exceptionInfo.context.variables),
              exceptionInfo.throwable
            ),
          )
        }
      case None =>
        ()
    }
  }

  private def performStorageOperation(actionOnStorage: LiveDataCollectingListenerStorage => Unit): Unit = {
    LiveDataCollectingListenerStorageHolder.withStorage(
      processName = processIdWithName.name,
      maxNumberOfRecords = maxNumberOfRecords,
      throughputTimeWindowInSeconds = throughputTimeWindowInSeconds
    )(actionOnStorage)
    uploaderConfig.foreach(LiveDataUploaderHolder.ensureLiveDataUploaderIsActive(processIdWithName, deploymentId, _))
  }

  override final def close(): Unit = ()

  private def sampleFromContext(context: Context, timestamp: Instant): LiveDataSample =
    LiveDataSample(context.id, timestamp, encode(context.variables))

  private def encode(variables: Map[String, Any]): Map[String, Json] =
    variables.map { case (k, v) => k -> encode(v) }

  private def encode(value: Any): Json = variableEncoder(value)

}

object LiveDataCollectingListener {

  def createListenerFor(
      processIdWithName: ProcessIdWithName,
      // todo: option can be removed, when we fully migrate to newdeployment.DeploymentId
      deploymentIdOpt: Option[DeploymentId],
      liveDataEnabledConfig: LiveDataPreviewMode.Enabled
  ): LiveDataCollectingListener = {
    LiveDataCollectingListenerStorageHolder.cleanResults(processIdWithName.name)
    val liveDataUploaderConfigOpt = liveDataEnabledConfig.liveDataStorage match {
      case dbStorage: LiveDataStorage.DesignerDb =>
        Some(
          LiveDataUploaderConfig(
            intervalSeconds = dbStorage.uploadIntervalInSeconds,
            uploaderInactivityTimeoutInSeconds = dbStorage.uploaderInactivityTimeoutInSeconds,
            dbUrl = dbStorage.url,
            dbUser = dbStorage.user,
            dbPassword = dbStorage.password,
            dbSchema = dbStorage.schema,
          )
        )
      case _ =>
        None
    }
    new LiveDataCollectingListener(
      processIdWithName,
      deploymentIdOpt,
      liveDataUploaderConfigOpt,
      liveDataEnabledConfig.maxNumberOfRecords,
      liveDataEnabledConfig.throughputTimeWindowInSeconds
    )
  }

}
