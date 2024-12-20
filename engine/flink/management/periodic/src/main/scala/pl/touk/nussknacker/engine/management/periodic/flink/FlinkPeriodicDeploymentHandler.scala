package pl.touk.nussknacker.engine.management.periodic.flink

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.JobID
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.periodic.model.{DeploymentWithRuntimeParams, RuntimeParams}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.common.periodic.PeriodicDeploymentHandler
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId}
import pl.touk.nussknacker.engine.management.periodic.flink.FlinkPeriodicDeploymentHandler.jarFileNameRuntimeParam
import pl.touk.nussknacker.engine.management.rest.{FlinkClient, HttpFlinkClient}
import pl.touk.nussknacker.engine.management.{
  FlinkConfig,
  FlinkDeploymentManager,
  FlinkModelJarProvider,
  FlinkStreamingRestManager
}
import pl.touk.nussknacker.engine.modelconfig.InputConfigDuringExecution
import pl.touk.nussknacker.engine.{BaseModelData, newdeployment}
import sttp.client3.SttpBackend

import java.nio.file.{Files, Path, Paths}
import scala.concurrent.{ExecutionContext, Future}

private[periodic] object FlinkPeriodicDeploymentHandler {

  val jarFileNameRuntimeParam = "jarFileName"

  def apply(flinkConfig: FlinkConfig, jarsDir: String, modelData: BaseModelData)(
      implicit backend: SttpBackend[Future, Any],
      ec: ExecutionContext
  ): PeriodicDeploymentHandler = {
    new FlinkPeriodicDeploymentHandler(
      flinkClient = HttpFlinkClient.createUnsafe(flinkConfig),
      jarsDir = Paths.get(jarsDir),
      inputConfigDuringExecution = modelData.inputConfigDuringExecution,
      modelJarProvider = new FlinkModelJarProvider(modelData.modelClassLoaderUrls)
    )
  }

}

// Used by [[PeriodicProcessService]].
class FlinkPeriodicDeploymentHandler(
    flinkClient: FlinkClient,
    jarsDir: Path,
    inputConfigDuringExecution: InputConfigDuringExecution,
    modelJarProvider: FlinkModelJarProvider
) extends PeriodicDeploymentHandler
    with LazyLogging {

  import scala.concurrent.ExecutionContext.Implicits.global

  override def prepareDeploymentWithRuntimeParams(
      processVersion: ProcessVersion,
  ): Future[DeploymentWithRuntimeParams] = {
    logger.info(s"Prepare deployment for scenario: $processVersion")
    copyJarToLocalDir(processVersion).map { jarFileName =>
      DeploymentWithRuntimeParams(
        processVersion = processVersion,
        runtimeParams = RuntimeParams(Map(jarFileNameRuntimeParam -> jarFileName))
      )
    }
  }

  override def provideInputConfigDuringExecutionJson(): Future[InputConfigDuringExecution] =
    Future.successful(inputConfigDuringExecution)

  private def copyJarToLocalDir(processVersion: ProcessVersion): Future[String] = Future {
    jarsDir.toFile.mkdirs()
    val jarFileName =
      s"${processVersion.processName}-${processVersion.versionId.value}-${System.currentTimeMillis()}.jar"
    val jarPath = jarsDir.resolve(jarFileName)
    Files.copy(modelJarProvider.getJobJar().toPath, jarPath)
    logger.info(s"Copied current model jar to $jarPath")
    jarFileName
  }

  override def deployWithRuntimeParams(
      deployment: DeploymentWithRuntimeParams,
      inputConfigDuringExecutionJson: String,
      deploymentData: DeploymentData,
      canonicalProcess: CanonicalProcess,
  ): Future[Option[ExternalDeploymentId]] = {
    val processVersion = deployment.processVersion
    deployment.runtimeParams.params.get(jarFileNameRuntimeParam) match {
      case Some(jarFileName) =>
        logger.info(
          s"Deploying scenario ${processVersion.processName}, version id: ${processVersion.versionId} and jar: $jarFileName"
        )
        val jarFile = jarsDir.resolve(jarFileName).toFile
        val args = FlinkDeploymentManager.prepareProgramArgs(
          inputConfigDuringExecutionJson,
          processVersion,
          deploymentData,
          canonicalProcess,
        )
        flinkClient.runProgram(
          jarFile,
          FlinkStreamingRestManager.MainClassName,
          args,
          None,
          deploymentData.deploymentId.toNewDeploymentIdOpt.map(toJobId)
        )
      case None =>
        logger.error(
          s"Cannot deploy scenario ${processVersion.processName}, version id: ${processVersion.versionId}: jar file name not present"
        )
        Future.successful(None)
    }
  }

  override def cleanAfterDeployment(runtimeParams: RuntimeParams): Future[Unit] = {
    runtimeParams.params.get(jarFileNameRuntimeParam) match {
      case Some(jarFileName) =>
        logger.info(s"Deleting jar: $jarFileName")
        for {
          _ <- deleteLocalJar(jarFileName)
          _ <- flinkClient.deleteJarIfExists(jarFileName)
        } yield ()
      case None =>
        logger.warn(s"Jar file name not present among runtime params: ${runtimeParams}")
        Future.unit
    }

  }

  private def deleteLocalJar(jarFileName: String): Future[Unit] = Future {
    val jarPath = jarsDir.resolve(jarFileName)
    val deleted = Files.deleteIfExists(jarPath)
    logger.info(s"Deleted: ($deleted) jar in: $jarPath")
  }

  private def toJobId(did: newdeployment.DeploymentId) = {
    new JobID(did.value.getLeastSignificantBits, did.value.getMostSignificantBits).toHexString
  }

}
