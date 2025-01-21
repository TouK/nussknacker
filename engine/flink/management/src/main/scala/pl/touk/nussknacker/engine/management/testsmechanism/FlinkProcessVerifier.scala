package pl.touk.nussknacker.engine.management.testsmechanism

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.{ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.util.{MetaDataExtractor, ReflectiveMethodInvoker}

import scala.concurrent.Future
import scala.util.Using
import scala.util.control.NonFatal

class FlinkProcessVerifier(modelData: ModelData) extends LazyLogging {

  // We use reflection, because we don't want to bundle flinkExecutor.jar inside flinkDeploymentManager assembly jar
  // because it is already in separate assembly for purpose of sending it to Flink during deployment.
  // Other option would be to add flinkExecutor.jar to classpath from which Flink DM is loaded
  private val methodInvoker = new ReflectiveMethodInvoker[Unit](
    modelData.modelClassLoader.classLoader,
    "pl.touk.nussknacker.engine.process.runner.FlinkVerificationMain",
    "run"
  )

  def verify(
      processVersion: ProcessVersion,
      canonicalProcess: CanonicalProcess,
      savepointPath: String
  ): Future[Unit] = {
    val parallelism = MetaDataExtractor
      .extractTypeSpecificDataOrDefault[StreamMetaData](canonicalProcess.metaData, StreamMetaData())
      .parallelism
      .getOrElse(1)
    val processId = processVersion.processName
    try {
      logger.info(s"Starting to verify $processId")
      // TODO: reuse a single mini cluster between each verifications
      Using.resource(TestsMechanismMiniClusterFactory.createConfiguredMiniCluster(parallelism)) { miniCluster =>
        val env = TestsMechanismStreamExecutionEnvironmentFactory.createStreamExecutionEnvironment(
          parallelism,
          new Configuration()
        )

        methodInvoker.invokeStaticMethod(
          miniCluster,
          env,
          modelData,
          canonicalProcess,
          processVersion,
          DeploymentData.empty,
          savepointPath
        )
      }
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
