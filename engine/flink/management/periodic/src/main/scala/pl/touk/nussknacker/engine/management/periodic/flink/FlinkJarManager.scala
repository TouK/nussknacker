package pl.touk.nussknacker.engine.management.periodic.flink

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{DeploymentData, ExternalDeploymentId, GraphProcess}
import pl.touk.nussknacker.engine.management.periodic.model.DeploymentWithJarData
import pl.touk.nussknacker.engine.management.periodic.{EnrichDeploymentWithJarData, JarManager, PeriodicBatchConfig, model}
import pl.touk.nussknacker.engine.management.rest.{FlinkClient, HttpFlinkClient}
import pl.touk.nussknacker.engine.management.{FlinkConfig, FlinkModelJar, FlinkDeploymentManager, FlinkStreamingRestManager}
import pl.touk.nussknacker.engine.modelconfig.InputConfigDuringExecution
import sttp.client.{NothingT, SttpBackend}

import java.io.File
import java.nio.file.{Files, Path, Paths}
import scala.concurrent.{ExecutionContext, Future}

private[periodic] object FlinkJarManager {
  def apply(flinkConfig: FlinkConfig,
            periodicBatchConfig: PeriodicBatchConfig,
            modelData: ModelData,
            enrichDeploymentWithJarData: EnrichDeploymentWithJarData)
           (implicit backend: SttpBackend[Future, Nothing, NothingT], ec: ExecutionContext): JarManager = {
    new FlinkJarManager(
      flinkClient = new HttpFlinkClient(flinkConfig),
      jarsDir = Paths.get(periodicBatchConfig.jarsDir),
      modelConfig = modelData.inputConfigDuringExecution,
      createCurrentModelJarFile = new FlinkModelJar().buildJobJar(modelData),
      enrichDeploymentWithJarData = enrichDeploymentWithJarData
    )
  }
}

// Used by [[PeriodicProcessService]].
private[periodic] class FlinkJarManager(flinkClient: FlinkClient,
                                        jarsDir: Path,
                                        modelConfig: InputConfigDuringExecution,
                                        createCurrentModelJarFile: => File,
                                        enrichDeploymentWithJarData: EnrichDeploymentWithJarData)
  extends JarManager with LazyLogging {

  import scala.concurrent.ExecutionContext.Implicits.global

  private lazy val currentModelJarFile = createCurrentModelJarFile

  override def prepareDeploymentWithJar(processVersion: ProcessVersion,
                                        processJson: String): Future[DeploymentWithJarData] = {
    logger.info(s"Prepare deployment for scenario: $processVersion")
    copyJarToLocalDir(processVersion).flatMap { jarFileName =>
      val deploymentWithJarData = model.DeploymentWithJarData(
        processVersion = processVersion,
        processJson = processJson,
        modelConfig = modelConfig.serialized,
        jarFileName = jarFileName
      )
      enrichDeploymentWithJarData(deploymentWithJarData)
    }
  }

  private def copyJarToLocalDir(processVersion: ProcessVersion): Future[String] = Future {
    jarsDir.toFile.mkdirs()
    val jarFileName = s"${processVersion.processName.value}-${processVersion.versionId}-${System.currentTimeMillis()}.jar"
    val jarPath = jarsDir.resolve(jarFileName)
    Files.copy(currentModelJarFile.toPath, jarPath)
    logger.info(s"Copied current model jar to $jarPath")
    jarFileName
  }

  override def deployWithJar(deploymentWithJarData: DeploymentWithJarData, deploymentData: DeploymentData): Future[Option[ExternalDeploymentId]] = {
    val processVersion = deploymentWithJarData.processVersion
    logger.info(s"Deploying scenario ${processVersion.processName.value}, version id: ${processVersion.versionId} and jar: ${deploymentWithJarData.jarFileName}")
    val jarFile = jarsDir.resolve(deploymentWithJarData.jarFileName).toFile
    val args = FlinkDeploymentManager.prepareProgramArgs(deploymentWithJarData.modelConfig,
      processVersion,
      deploymentData,
      GraphProcess(deploymentWithJarData.processJson))
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
