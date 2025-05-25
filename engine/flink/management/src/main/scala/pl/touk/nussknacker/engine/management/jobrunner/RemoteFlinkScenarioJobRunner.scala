package pl.touk.nussknacker.engine.management.jobrunner

import io.circe.syntax.EncoderOps
import org.apache.flink.api.common.JobID
import pl.touk.nussknacker.engine.BaseModelDataProvider
import pl.touk.nussknacker.engine.ModelConfig.LiveDataPreviewMode
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{
  DMRunDeploymentCommand,
  LiveDataPreviewStoredInTheDb,
  LiveDataPreviewSupport,
  NoLiveDataPreviewSupport
}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.management.FlinkDeploymentManager.DeploymentIdOps
import pl.touk.nussknacker.engine.management.jobrunner.RemoteFlinkScenarioJobRunner.{prepareProgramArgs, MainClassName}
import pl.touk.nussknacker.engine.management.rest.FlinkClient

import scala.concurrent.{ExecutionContext, Future}

class RemoteFlinkScenarioJobRunner(modelDataProvider: BaseModelDataProvider, client: FlinkClient)(
    implicit ec: ExecutionContext
) extends FlinkScenarioJobRunner {

  private val modelJarProvider = new FlinkModelJarProvider(modelDataProvider.modelClassLoader.getURLs.toList)

  override def runScenarioJob(
      command: DMRunDeploymentCommand,
      savepointPathOpt: Option[String]
  ): Future[Option[JobID]] = {
    import command._
    val args = prepareProgramArgs(
      modelDataProvider.getCurrentModelData().inputConfigDuringExecution.serialized,
      processVersion,
      deploymentData,
      canonicalProcess
    )
    client.runProgram(
      modelJarProvider.getJobJar(),
      MainClassName,
      args,
      savepointPathOpt,
      command.deploymentData.deploymentId.toNewDeploymentIdOpt.map(_.toJobID)
    )
  }

  override def liveDataPreviewSupport: LiveDataPreviewSupport = {
    val modelConfig = modelDataProvider.getCurrentModelData().modelConfig
    modelConfig.liveDataPreviewMode match {
      // Remote Flink job runner requires configuring db uploader, which synchronizes the live results on Flink with designer db
      case LiveDataPreviewMode.Enabled(_, _, Some(_)) =>
        LiveDataPreviewStoredInTheDb
      case LiveDataPreviewMode.Enabled(_, _, None) | LiveDataPreviewMode.Disabled =>
        NoLiveDataPreviewSupport
    }

  }

}

object RemoteFlinkScenarioJobRunner {

  def prepareProgramArgs(
      serializedConfig: String,
      processVersion: ProcessVersion,
      deploymentData: DeploymentData,
      canonicalProcess: CanonicalProcess
  ): List[String] =
    List(
      canonicalProcess.asJson.spaces2,
      processVersion.asJson.spaces2,
      deploymentData.asJson.spaces2,
      serializedConfig
    )

  private[management] val MainClassName = "pl.touk.nussknacker.engine.process.runner.FlinkStandaloneScenarioMain"

}
