package pl.touk.nussknacker.engine.management.periodic.flink

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.JobID
import pl.touk.nussknacker.engine.{BaseModelData, newdeployment}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId}
import pl.touk.nussknacker.engine.management.periodic.model.DeploymentWithJarData
import pl.touk.nussknacker.engine.management.periodic.{JarManager, PeriodicBatchConfig}
import pl.touk.nussknacker.engine.management.rest.{FlinkClient, HttpFlinkClient}
import pl.touk.nussknacker.engine.management.{
  FlinkConfig,
  FlinkDeploymentManager,
  FlinkModelJarProvider,
  FlinkStreamingRestManager
}
import pl.touk.nussknacker.engine.modelconfig.InputConfigDuringExecution
import sttp.client3.SttpBackend

import java.nio.file.{Files, Path, Paths}
import scala.concurrent.{ExecutionContext, Future}

private[periodic] object FlinkJarManager {

  def apply(flinkConfig: FlinkConfig, periodicBatchConfig: PeriodicBatchConfig, modelData: BaseModelData)(
      implicit backend: SttpBackend[Future, Any],
      ec: ExecutionContext
  ): JarManager = {
    new FlinkJarManager(
      flinkClient = HttpFlinkClient.createUnsafe(flinkConfig),
      jarsDir = Paths.get(periodicBatchConfig.jarsDir),
      inputConfigDuringExecution = modelData.inputConfigDuringExecution,
      modelJarProvider = new FlinkModelJarProvider(modelData.modelClassLoaderUrls)
    )
  }

}

// Used by [[PeriodicProcessService]].
private[periodic] class FlinkJarManager(
    flinkClient: FlinkClient,
    jarsDir: Path,
    inputConfigDuringExecution: InputConfigDuringExecution,
    modelJarProvider: FlinkModelJarProvider
) extends JarManager
    with LazyLogging {

  import scala.concurrent.ExecutionContext.Implicits.global

  override def prepareDeploymentWithJar(
      processVersion: ProcessVersion,
      canonicalProcess: CanonicalProcess
  ): Future[DeploymentWithJarData[CanonicalProcess]] = {
    logger.info(s"Prepare deployment for scenario: $processVersion")
    copyJarToLocalDir(processVersion).map { jarFileName =>
      DeploymentWithJarData(
        processName = processVersion.processName,
        versionId = processVersion.versionId,
        process = canonicalProcess,
        inputConfigDuringExecutionJson = inputConfigDuringExecution.serialized,
        jarFileName = jarFileName
      )
    }
  }

  private def copyJarToLocalDir(processVersion: ProcessVersion): Future[String] = Future {
    jarsDir.toFile.mkdirs()
    val jarFileName =
      s"${processVersion.processName}-${processVersion.versionId.value}-${System.currentTimeMillis()}.jar"
    val jarPath = jarsDir.resolve(jarFileName)
    Files.copy(modelJarProvider.getJobJar().toPath, jarPath)
    logger.info(s"Copied current model jar to $jarPath")
    jarFileName
  }

  override def deployWithJar(
      deploymentWithJarData: DeploymentWithJarData[CanonicalProcess],
      deploymentData: DeploymentData
  ): Future[Option[ExternalDeploymentId]] = {
    logger.info(
      s"Deploying scenario ${deploymentWithJarData.processName}, version id: ${deploymentWithJarData.versionId} and jar: ${deploymentWithJarData.jarFileName}"
    )
    val jarFile = jarsDir.resolve(deploymentWithJarData.jarFileName).toFile
    val args = FlinkDeploymentManager.prepareProgramArgs(
      deploymentWithJarData.inputConfigDuringExecutionJson,
      ProcessVersion.empty
        .copy(processName = deploymentWithJarData.processName, versionId = deploymentWithJarData.versionId),
      deploymentData,
      deploymentWithJarData.process
    )
    flinkClient.runProgram(
      jarFile,
      FlinkStreamingRestManager.MainClassName,
      args,
      None,
      deploymentData.deploymentId.toNewDeploymentIdOpt.map(toJobId)
    )
  }

  override def deleteJar(jarFileName: String): Future[Unit] = {
    logger.info(s"Deleting jar: $jarFileName")
    for {
      _ <- deleteLocalJar(jarFileName)
      _ <- flinkClient.deleteJarIfExists(jarFileName)
    } yield ()
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
