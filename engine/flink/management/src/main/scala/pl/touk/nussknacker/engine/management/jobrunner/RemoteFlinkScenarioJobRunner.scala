package pl.touk.nussknacker.engine.management.jobrunner

import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.BaseModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.DMRunDeploymentCommand
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId}
import pl.touk.nussknacker.engine.management.FlinkDeploymentManager.DeploymentIdOps
import pl.touk.nussknacker.engine.management.jobrunner.RemoteFlinkScenarioJobRunner.{MainClassName, prepareProgramArgs}
import pl.touk.nussknacker.engine.management.rest.FlinkClient

import scala.concurrent.Future

class RemoteFlinkScenarioJobRunner(modelData: BaseModelData, client: FlinkClient) extends FlinkScenarioJobRunner {

  private val modelJarProvider = new FlinkModelJarProvider(modelData.modelClassLoaderUrls)

  override def runScenarioJob(
      command: DMRunDeploymentCommand,
      savepointPathOpt: Option[String]
  ): Future[Option[ExternalDeploymentId]] = {
    import command._
    val args = prepareProgramArgs(
      modelData.inputConfigDuringExecution.serialized,
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

  private[management] val MainClassName = "pl.touk.nussknacker.engine.process.runner.FlinkStreamingProcessMain"

}
