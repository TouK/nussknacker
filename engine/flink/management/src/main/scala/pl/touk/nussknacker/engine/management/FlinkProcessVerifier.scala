package pl.touk.nussknacker.engine.management

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.util.StaticMethodRunner

import java.net.URLClassLoader
import scala.concurrent.Future
import scala.util.control.NonFatal

class FlinkProcessVerifier(modelData: ModelData) extends StaticMethodRunner(modelData.modelClassLoader.classLoader,
  "pl.touk.nussknacker.engine.process.runner.FlinkVerificationMain", "run") with LazyLogging {

  def verify(processVersion: ProcessVersion, processJson: String, savepointPath: String): Future[Unit] = {
    val processId = processVersion.processName
    try {
      logger.info(s"Starting to verify $processId")
      val printed = modelData.modelClassLoader.classLoader match {
        case a: URLClassLoader => a.getURLs.toList
        case a => a
      }
      logger.info("FLINKBASECOMPONENT classloader: " + printed)
      tryToInvoke(modelData, processJson, processVersion, DeploymentData.empty, savepointPath, new Configuration())
      logger.info(s"Verification of $processId successful")
      Future.successful(())
    } catch {
      case NonFatal(e) =>
        logger.info(s"Failed to verify $processId", e)
        Future.failed(
          new IllegalArgumentException("State is incompatible, please stop scenario and start again with clean state", e))
    }
  }
}
