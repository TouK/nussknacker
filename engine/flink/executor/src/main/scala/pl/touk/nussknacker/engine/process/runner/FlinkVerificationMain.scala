package pl.touk.nussknacker.engine.process.runner

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.process.compiler.VerificationFlinkProcessCompilerDataFactory
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, FlinkJobConfig}
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListenerHolder, TestRunId, TestServiceInvocationCollector}

import scala.util.Using

object FlinkVerificationMain extends FlinkRunner {

  def run(
      modelData: ModelData,
      process: CanonicalProcess,
      processVersion: ProcessVersion,
      deploymentData: DeploymentData,
      savepointPath: String,
      configuration: Configuration
  ): Unit =
    new FlinkVerificationMain(modelData, process, processVersion, deploymentData, savepointPath, configuration)
      .runTest()

}

class FlinkVerificationMain(
    val modelData: ModelData,
    val process: CanonicalProcess,
    processVersion: ProcessVersion,
    deploymentData: DeploymentData,
    savepointPath: String,
    val configuration: Configuration
) extends FlinkStubbedRunner
    with LazyLogging {

  def runTest(): Unit = {
    val collectingListener = ResultsCollectingListenerHolder.registerTestEngineListener
    try {
      val resultCollector = new TestServiceInvocationCollector(collectingListener)
      val registrar       = prepareRegistrar()
      val env             = createEnv

      try {
        registrar.register(env, process, processVersion, deploymentData, resultCollector)
        execute(env, SavepointRestoreSettings.forPath(savepointPath, true))
      } finally {
        logger.debug(s"Closing LocalEnvironment for model with classpath: ${modelData.modelClassLoader}")
        env.close()
      }
    } finally {
      collectingListener.close()
    }
  }

  protected def prepareRegistrar(): FlinkProcessRegistrar = {
    FlinkProcessRegistrar(
      VerificationFlinkProcessCompilerDataFactory(process, modelData),
      FlinkJobConfig.parse(modelData.modelConfig),
      ExecutionConfigPreparer.defaultChain(modelData)
    )
  }

}
