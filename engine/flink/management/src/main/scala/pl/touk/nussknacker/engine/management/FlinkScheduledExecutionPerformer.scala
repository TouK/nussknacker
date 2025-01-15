package pl.touk.nussknacker.engine.management

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.JobID
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.scheduler.model.{DeploymentWithRuntimeParams, RuntimeParams}
import pl.touk.nussknacker.engine.api.deployment.scheduler.services.ScheduledExecutionPerformer
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, ExternalDeploymentId}
import pl.touk.nussknacker.engine.management.FlinkScheduledExecutionPerformer.jarFileNameRuntimeParam
import pl.touk.nussknacker.engine.management.rest.{FlinkClient, HttpFlinkClient}
import pl.touk.nussknacker.engine.modelconfig.InputConfigDuringExecution
import pl.touk.nussknacker.engine.util.config.ConfigEnrichments.RichConfig
import pl.touk.nussknacker.engine.{BaseModelData, DeploymentManagerDependencies, newdeployment}

import java.nio.file.{Files, Path, Paths}
import scala.concurrent.Future

object FlinkScheduledExecutionPerformer {

  val jarFileNameRuntimeParam = "jarFileName"

  def create(
      modelData: BaseModelData,
      dependencies: DeploymentManagerDependencies,
      config: Config,
  ): ScheduledExecutionPerformer = {
    import dependencies._
    import net.ceedubs.ficus.Ficus._
    import net.ceedubs.ficus.readers.ArbitraryTypeReader._
    val flinkConfig = config.rootAs[FlinkConfig]
    new FlinkScheduledExecutionPerformer(
      flinkClient = HttpFlinkClient.createUnsafe(flinkConfig),
      jarsDir = Paths.get(config.getString("scheduling.jarsDir")),
      inputConfigDuringExecution = modelData.inputConfigDuringExecution,
      modelJarProvider = new FlinkModelJarProvider(modelData.modelClassLoaderUrls)
    )
  }

}

// Used by [[PeriodicProcessService]].
class FlinkScheduledExecutionPerformer(
    flinkClient: FlinkClient,
    jarsDir: Path,
    inputConfigDuringExecution: InputConfigDuringExecution,
    modelJarProvider: FlinkModelJarProvider
) extends ScheduledExecutionPerformer
    with LazyLogging {

  import scala.concurrent.ExecutionContext.Implicits.global

  override def prepareDeploymentWithRuntimeParams(
      processVersion: ProcessVersion,
  ): Future[DeploymentWithRuntimeParams] = {
    logger.info(s"Prepare deployment for scenario: $processVersion")
    copyJarToLocalDir(processVersion).map { jarFileName =>
      DeploymentWithRuntimeParams(
        processId = Some(processVersion.processId),
        processName = processVersion.processName,
        versionId = processVersion.versionId,
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
      processVersion: ProcessVersion,
  ): Future[Option[ExternalDeploymentId]] = {
    deployment.runtimeParams.params.get(jarFileNameRuntimeParam) match {
      case Some(jarFileName) =>
        logger.info(
          s"Deploying scenario ${deployment.processName}, version id: ${deployment.versionId} and jar: $jarFileName"
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
          s"Cannot deploy scenario ${deployment.processName}, version id: ${deployment.versionId}: jar file name not present"
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
