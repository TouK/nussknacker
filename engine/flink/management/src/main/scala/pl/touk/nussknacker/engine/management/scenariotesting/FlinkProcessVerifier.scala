package pl.touk.nussknacker.engine.management.scenariotesting

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.util.ReflectiveMethodInvoker

import scala.concurrent.Future
import scala.util.control.NonFatal

class FlinkProcessVerifier(modelData: ModelData, miniClusterWrapperOpt: Option[Any]) extends LazyLogging {

  // We use reflection, because we don't want to bundle flinkExecutor.jar inside flinkDeploymentManager assembly jar
  // because it is already in separate assembly for purpose of sending it to Flink during deployment.
  // Other option would be to add flinkExecutor.jar to classpath from which Flink DM is loaded
  private val mainRunner = new ReflectiveMethodInvoker[Unit](
    modelData.modelClassLoader,
    "pl.touk.nussknacker.engine.process.scenariotesting.FlinkVerificationMain",
    "run"
  )

  def verify(
      processVersion: ProcessVersion,
      canonicalProcess: CanonicalProcess,
      savepointPath: String
  ): Future[Unit] = {
    val processId = processVersion.processName
    try {
      logger.info(s"Starting to verify $processId")
      mainRunner.invokeStaticMethod(
        miniClusterWrapperOpt,
        modelData,
        canonicalProcess,
        processVersion,
        savepointPath
      )
      logger.info(s"Verification of $processId successful")
      Future.successful(())
    } catch {
      case NonFatal(e) =>
        logger.info(s"Failed to verify $processId", e)
        Future.failed(
          new IllegalArgumentException(
            "State is incompatible, please stop scenario and start again with clean state",
            e
          )
        )
    }
  }

}
