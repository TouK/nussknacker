package pl.touk.nussknacker.engine.management.scenariotesting

import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.management.ScenarioTestingConfig
import pl.touk.nussknacker.engine.util.ReflectiveMethodInvoker
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader

object ScenarioTestingMiniClusterWrapperFactory {

  // We use reflection, because we don't want to bundle flinkExecutor.jar inside flinkDeploymentManager assembly jar
  // because it is already in separate assembly for purpose of sending it to Flink during deployment.
  // Other option would be to add flinkExecutor.jar to classpath from which Flink DM is loaded
  def createIfConfigured(modelClassLoader: ModelClassLoader, config: ScenarioTestingConfig): Option[AutoCloseable] = {
    if (config.reuseMiniClusterForScenarioTesting || config.reuseMiniClusterForScenarioStateVerification) {
      Some(create(modelClassLoader, config.parallelism, config.streamExecutionConfig))
    } else {
      None
    }
  }

  private[nussknacker] def create(
      modelClassLoader: ModelClassLoader,
      parallelism: Int,
      streamExecutionConfig: Configuration
  ): AutoCloseable = {
    val methodInvoker = new ReflectiveMethodInvoker[AutoCloseable](
      modelClassLoader,
      "pl.touk.nussknacker.engine.process.scenariotesting.ScenarioTestingMiniClusterWrapper",
      "create"
    )
    methodInvoker.invokeStaticMethod(parallelism, streamExecutionConfig)
  }

}
