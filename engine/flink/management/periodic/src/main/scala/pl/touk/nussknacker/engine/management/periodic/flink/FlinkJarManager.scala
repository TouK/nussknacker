package pl.touk.nussknacker.engine.management.periodic.flink

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.BaseModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId}
import pl.touk.nussknacker.engine.management.periodic.model.DeploymentWithJarData
import pl.touk.nussknacker.engine.management.periodic.{JarManager, PeriodicBatchConfig}
import pl.touk.nussknacker.engine.management.rest.{FlinkClient, HttpFlinkClient}
import pl.touk.nussknacker.engine.management.{
  FlinkConfig,
  FlinkDeploymentManager,
  FlinkModelJar,
  FlinkStreamingRestManager
}
import pl.touk.nussknacker.engine.modelconfig.InputConfigDuringExecution
import sttp.client3.SttpBackend

import java.io.File
import java.nio.file.{Files, Path, Paths}
import scala.concurrent.{ExecutionContext, Future}

private[periodic] object FlinkJarManager {

  def apply(flinkConfig: FlinkConfig, periodicBatchConfig: PeriodicBatchConfig, modelData: BaseModelData)(
      implicit backend: SttpBackend[Future, Any],
      ec: ExecutionContext
  ): JarManager = {
    new FlinkJarManager(
      flinkClient = new HttpFlinkClient(flinkConfig),
      jarsDir = Paths.get(periodicBatchConfig.jarsDir),
      inputConfigDuringExecution = modelData.inputConfigDuringExecution,
      createCurrentModelJarFile = new FlinkModelJar().buildJobJar(modelData)
    )
  }

}

// Used by [[PeriodicProcessService]].
private[periodic] class FlinkJarManager(
    flinkClient: FlinkClient,
    jarsDir: Path,
    inputConfigDuringExecution: InputConfigDuringExecution,
    createCurrentModelJarFile: => File
) extends JarManager
    with LazyLogging {

  import scala.concurrent.ExecutionContext.Implicits.global

  private lazy val currentModelJarFile = createCurrentModelJarFile

  override def prepareDeploymentWithJar(
      processVersion: ProcessVersion,
  ): Future[DeploymentWithJarData] = {
    logger.info(s"Prepare deployment for scenario: $processVersion")
    copyJarToLocalDir(processVersion).map { jarFileName =>
      DeploymentWithJarData(
        processVersion = processVersion,
        inputConfigDuringExecutionJson = inputConfigDuringExecution.serialized,
        jarFileName = jarFileName
      )
    }
  }

  private def copyJarToLocalDir(processVersion: ProcessVersion): Future[String] = Future {
    jarsDir.toFile.mkdirs()
    val jarFileName =
      s"${processVersion.processName.value}-${processVersion.versionId.value}-${System.currentTimeMillis()}.jar"
    val jarPath = jarsDir.resolve(jarFileName)
    Files.copy(currentModelJarFile.toPath, jarPath)
    logger.info(s"Copied current model jar to $jarPath")
    jarFileName
  }

  override def deployWithJar(
      deploymentWithJarData: DeploymentWithJarData,
      deploymentData: DeploymentData,
      canonicalProcess: CanonicalProcess
  ): Future[Option[ExternalDeploymentId]] = {
    val processVersion = deploymentWithJarData.processVersion
    logger.info(
      s"Deploying scenario ${processVersion.processName.value}, version id: ${processVersion.versionId} and jar: ${deploymentWithJarData.jarFileName}"
    )
    val jarFile = jarsDir.resolve(deploymentWithJarData.jarFileName).toFile
    val args = FlinkDeploymentManager.prepareProgramArgs(
      deploymentWithJarData.inputConfigDuringExecutionJson,
      processVersion,
      deploymentData,
      canonicalProcess
    )
    flinkClient.runProgram(jarFile, FlinkStreamingRestManager.MainClassName, args, None)
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

}
