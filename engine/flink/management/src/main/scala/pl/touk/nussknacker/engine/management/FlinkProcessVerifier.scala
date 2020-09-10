package pl.touk.nussknacker.engine.management

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.util.StaticMethodRunner

import scala.concurrent.Future
import scala.util.control.NonFatal

class FlinkProcessVerifier(modelData: ModelData) extends StaticMethodRunner(modelData.modelClassLoader.classLoader,
  "pl.touk.nussknacker.engine.process.runner.FlinkVerificationMain", "run") with LazyLogging {

  def verify(processVersion: ProcessVersion, processJson: String, savepointPath: String): Future[Unit] = {
    val processId = processVersion.processName
    try {
      logger.info(s"Starting to verify $processId")
      tryToInvoke(modelData, processJson, processVersion, savepointPath, new Configuration())
      logger.info(s"Verification of $processId successful")
      Future.successful(())
    } catch {
      case NonFatal(e) =>
        logger.info(s"Failed to verify $processId", e)
        Future.failed(
          new IllegalArgumentException("State is incompatible, please stop process and start again with clean state", e))
    }
  }
}
