package pl.touk.nussknacker.engine.livedata

import com.typesafe.scalalogging.LazyLogging
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
import scala.concurrent.duration.Duration
import scala.util.Try

// This class must be serializable. It means, that when deserializing, we lose the reference to it.
// The actual data is stored in the LiveDataCollectingListenerHolder, and all instances of LiveDataCollectingListener can access the data.
class LiveDataCollectingListener private[livedata] (
    processIdWithName: ProcessIdWithName,
    deploymentId: Option[DeploymentId],
    uploaderConfig: Option[LiveDataUploaderConfig],
    maxNumberOfRecords: Int,
    retentionTime: Duration,
    throughputTimeWindow: Duration,
) extends ProcessListener
    with Serializable {

  private val variableEncoder: Any => io.circe.Json = TestInterpreterRunner.testResultsVariableEncoder

  override def nodeEntered(
      nodeId: NodeId,
      context: Context,
      processMetaData: MetaData,
  ): Unit = ()

  override def transitionToNextNode(
      nodeId: NodeId,
      nextNodeId: NodeId,
      context: Context,
      processMetaData: MetaData,
  ): Unit = transitionToNextNode(nodeId, nextNodeId, context, Instant.now(), isDirectTransition = true)

  override def transitionFromFragmentStartToNodeAfterFragment(
      nodeId: NodeId,
      nextNodeId: NodeId,
      context: Context,
      processMetaData: MetaData,
  ): Unit = transitionToNextNode(nodeId, nextNodeId, context, Instant.now(), isDirectTransition = false)

  def transitionToNextNode(
      nodeId: NodeId,
      nextNodeId: NodeId,
      context: Context,
      timestamp: Instant,
      isDirectTransition: Boolean,
  ): Unit = performStorageOperation {
    _.addLiveDataSample(
      NodeTransition(nodeId, Some(nextNodeId), isDirectTransition),
      sampleFromContext(context, timestamp)
    )
  }

  override def processingFinishedInNode(
      nodeId: NodeId,
      context: Context,
      processMetaData: MetaData,
  ): Unit = performStorageOperation {
    _.addLiveDataSample(
      NodeTransition(nodeId, None, isDirectTransition = true),
      sampleFromContext(context, Instant.now())
    )
  }

  override def endEncountered(
      nodeId: NodeId,
      ref: String,
      context: Context,
      processMetaData: MetaData,
  ): Unit = ()

  override def deadEndEncountered(
      lastNodeId: NodeId,
      context: Context,
      processMetaData: MetaData,
  ): Unit = ()

  override def expressionEvaluated(
      nodeId: NodeId,
      expressionId: String,
      expression: String,
      context: Context,
      processMetaData: MetaData,
      result: Any,
  ): Unit = performStorageOperation {
    _.addExpressionEvaluationResult(
      nodeId,
      ExpressionEvaluationResult(context.id, Instant.now(), expressionId, encode(result)),
    )
  }

  override def serviceInvoked(
      nodeId: NodeId,
      id: String,
      context: Context,
      processMetaData: MetaData,
      result: Try[Any],
  ): Unit = performStorageOperation {
    _.addExternalServiceInvocation(
      nodeId,
      ExternalServiceInvocationResult(context.id, Instant.now(), id, result.map(encode).getOrElse(Json.Null)),
    )
  }

  override def exceptionThrown(
      exceptionInfo: NuExceptionInfo,
  ): Unit = {
    exceptionInfo.nodeComponentInfo match {
      case Some(nodeComponentInfo) =>
        performStorageOperation {
          _.addException(
            nodeComponentInfo.nodeId,
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
      retentionTime = retentionTime,
      throughputTimeWindow = throughputTimeWindow
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

object LiveDataCollectingListener extends LazyLogging {

  def createListenerFor(
      processIdWithName: ProcessIdWithName,
      // todo: option can be removed, when we fully migrate to newdeployment.DeploymentId
      deploymentIdOpt: Option[DeploymentId],
      liveDataEnabledConfig: LiveDataPreviewMode.Enabled,
      skipLiveDataUploaderWithReason: Option[String]
  ): LiveDataCollectingListener = {
    LiveDataCollectingListenerStorageHolder.cleanResults(processIdWithName.name)
    val liveDataUploaderConfigOpt = (liveDataEnabledConfig.liveDataStorage, skipLiveDataUploaderWithReason) match {
      case (_: LiveDataStorage.DesignerDb, Some(skipLiveDataUploaderReason)) =>
        logger.debug(
          s"liveDataPreview.storage is configured but $skipLiveDataUploaderReason. Live Data uploader will be disabled and data will be kept in the JVM memory only."
        )
        None
      case (dbStorage: LiveDataStorage.DesignerDb, None) =>
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
      case (LiveDataStorage.DesignerJvm, _) =>
        None
    }
    new LiveDataCollectingListener(
      processIdWithName,
      deploymentIdOpt,
      liveDataUploaderConfigOpt,
      liveDataEnabledConfig.maxNumberOfRecords,
      liveDataEnabledConfig.retentionTime,
      liveDataEnabledConfig.throughputTimeWindow
    )
  }

}
