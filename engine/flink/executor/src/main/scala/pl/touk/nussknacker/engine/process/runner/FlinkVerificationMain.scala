package pl.touk.nussknacker.engine.process.runner

import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.{ProcessVersion, StreamMetaData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.process.compiler.VerificationFlinkProcessCompilerDataFactory
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, FlinkJobConfig}
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListenerHolder, TestRunId, TestServiceInvocationCollector}
import pl.touk.nussknacker.engine.util.MetaDataExtractor

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
    modelData: ModelData,
    process: CanonicalProcess,
    processVersion: ProcessVersion,
    deploymentData: DeploymentData,
    savepointPath: String,
    configuration: Configuration
) {

  private val stubbedRunner = new FlinkStubbedRunner(modelData.modelClassLoader, configuration)

  def runTest(): Unit = {
    val collectingListener = ResultsCollectingListenerHolder.registerTestEngineListener
    val resultCollector    = new TestServiceInvocationCollector(collectingListener)
    val registrar          = prepareRegistrar()
    val parallelism = MetaDataExtractor
      .extractTypeSpecificDataOrDefault[StreamMetaData](process.metaData, StreamMetaData())
      .parallelism
      .getOrElse(1)
    val env = stubbedRunner.createEnv(parallelism)

    registrar.register(env, process, processVersion, deploymentData, resultCollector)
    stubbedRunner.execute(env, parallelism, process.name, SavepointRestoreSettings.forPath(savepointPath, true))
  }

  protected def prepareRegistrar(): FlinkProcessRegistrar = {
    FlinkProcessRegistrar(
      VerificationFlinkProcessCompilerDataFactory(process, modelData),
      FlinkJobConfig.parse(modelData.modelConfig),
      ExecutionConfigPreparer.defaultChain(modelData)
    )
  }

}
