package pl.touk.nussknacker.engine.management.jobrunner

import org.apache.flink.api.common.{JobExecutionResult, JobID}
import org.apache.flink.configuration.{Configuration, PipelineOptionsInternal}
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import pl.touk.nussknacker.engine.{livedata, BaseModelDataProvider}
import pl.touk.nussknacker.engine.ModelConfig.LiveDataPreviewMode
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.deployment.{
  DMRunDeploymentCommand,
  LiveDataPreviewSupport,
  LiveDataPreviewSupported,
  NoLiveDataPreviewSupport
}
import pl.touk.nussknacker.engine.api.deployment.LiveDataPreviewSupported._
import pl.touk.nussknacker.engine.api.deployment.LiveDataPreviewSupported.LiveDataError.NoLiveDataAvailableForScenario
import pl.touk.nussknacker.engine.api.process.ProcessIdWithName
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterWithServices
import pl.touk.nussknacker.engine.livedata.LiveDataCollectingListenerHolder
import pl.touk.nussknacker.engine.management.FlinkDeploymentManager.DeploymentIdOps
import pl.touk.nussknacker.engine.util.ReflectiveMethodInvoker

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

class FlinkMiniClusterScenarioJobRunner(
    miniClusterWithServices: FlinkMiniClusterWithServices,
    modelDataProvider: BaseModelDataProvider
)(implicit executionContext: ExecutionContext)
    extends FlinkScenarioJobRunner {

  // We use reflection, because we don't want to bundle flinkExecutor.jar inside deployment manager assembly jar
  // because it is already in separate assembly for purpose of sending it to Flink during deployment.
  // Other option would be to add flinkExecutor.jar to classpath from which DM is loaded
  private val jobInvoker = new ReflectiveMethodInvoker[JobExecutionResult](
    modelDataProvider.modelClassLoader,
    "pl.touk.nussknacker.engine.process.runner.FlinkScenarioJob",
    "run"
  )

  override def runScenarioJob(
      command: DMRunDeploymentCommand,
      savepointPathOpt: Option[String]
  ): Future[Option[JobID]] = {
    Future {
      miniClusterWithServices.withDetachedStreamExecutionEnvironment { env =>
        val conf = new Configuration()
        savepointPathOpt.foreach { savepointPath =>
          SavepointRestoreSettings.toConfiguration(SavepointRestoreSettings.forPath(savepointPath, true), conf)
        }
        command.deploymentData.deploymentId.toNewDeploymentIdOpt.map(_.toJobID).foreach { jobId =>
          conf.set(PipelineOptionsInternal.PIPELINE_FIXED_JOB_ID, jobId.toHexString)
        }
        env.configure(conf)
        val liveDataCollectingListener =
          modelDataProvider.getCurrentModelData().modelConfig.liveDataPreviewMode match {
            case LiveDataPreviewMode.Disabled =>
              None
            case LiveDataPreviewMode.Enabled(maxNumberOfSamples, throughputTimeWindowInSeconds, _) =>
              Some(
                LiveDataCollectingListenerHolder.createListenerFor(
                  command.processVersion.processName,
                  maxNumberOfSamples,
                  throughputTimeWindowInSeconds
                )
              )
          }
        val jobID = jobInvoker
          .invokeStaticMethod(
            modelDataProvider.getCurrentModelData(),
            command.canonicalProcess,
            command.processVersion,
            command.deploymentData,
            env,
            liveDataCollectingListener.toList,
          )
          .getJobID
        Some(jobID)
      }
    }
  }

  override def liveDataPreviewSupport: LiveDataPreviewSupport = {
    modelDataProvider.getCurrentModelData().modelConfig.liveDataPreviewMode match {
      case LiveDataPreviewMode.Enabled(_, _, _) =>
        new LiveDataPreviewSupported {
          override def getLiveData(processIdWithName: ProcessIdWithName): Future[Either[LiveDataError, LiveData]] =
            Future {
              LiveDataCollectingListenerHolder.getLiveDataPreview(processIdWithName.name) match {
                case Some(collected) => Right(toApi(collected))
                case None            => Left(NoLiveDataAvailableForScenario)
              }
            }
        }
      case LiveDataPreviewMode.Disabled =>
        NoLiveDataPreviewSupport
    }
  }

  private def toApi(collectedLiveData: livedata.CollectedLiveData): LiveData = {
    LiveData(
      timestamp = collectedLiveData.timestamp,
      nodeTransitions = collectedLiveData.nodeTransitions
        .map { case (nodeTransition, data) => toApi(nodeTransition) -> toApi(data) },
      invocationResults = collectedLiveData.invocationResults
        .map { case (nodeId, results) => NodeId(nodeId.id) -> results.map(toApi) },
      externalInvocationResults = collectedLiveData.externalInvocationResults
        .map { case (nodeId, results) => NodeId(nodeId.id) -> results.map(toApi) },
      exceptions = collectedLiveData.exceptions
        .map { case (nodeId, results) => NodeId(nodeId.id) -> results.map(toApi) },
    )
  }

  private def toApi(nodeTransition: livedata.NodeTransition): NodeTransition = {
    NodeTransition(nodeTransition.sourceNodeId, nodeTransition.destinationNodeId)
  }

  private def toApi(nodeTransition: livedata.LiveDataForNodeTransition): LiveDataForNodeTransition = {
    LiveDataForNodeTransition(
      samples = nodeTransition.samples.map(toApi),
      totalCount = nodeTransition.totalCount,
      currentThroughput = nodeTransition.currentThroughput,
    )
  }

  private def toApi(nodeTransition: livedata.LiveDataSample): LiveDataSample = {
    LiveDataSample(
      contextId = nodeTransition.contextId,
      timestamp = nodeTransition.timestamp,
      variables = nodeTransition.variables,
    )
  }

  private def toApi(nodeTransition: livedata.InvocationResult): InvocationResult = {
    InvocationResult(
      contextId = nodeTransition.contextId,
      timestamp = nodeTransition.timestamp,
      name = nodeTransition.name,
      value = nodeTransition.value,
    )
  }

  private def toApi(nodeTransition: livedata.ExceptionResult): ExceptionResult = {
    ExceptionResult(
      contextId = nodeTransition.contextId,
      timestamp = nodeTransition.timestamp,
      variables = nodeTransition.variables,
      throwable = nodeTransition.throwable,
    )
  }

}
