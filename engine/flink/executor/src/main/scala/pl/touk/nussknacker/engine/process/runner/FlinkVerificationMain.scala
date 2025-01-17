package pl.touk.nussknacker.engine.process.runner

import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import org.apache.flink.runtime.minicluster.MiniCluster
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.process.compiler.VerificationFlinkProcessCompilerDataFactory
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.process.testmechanism.FlinkStubbedRunner
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, FlinkJobConfig}
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListenerHolder, TestServiceInvocationCollector}

object FlinkVerificationMain extends FlinkRunner {

  def run(
      miniCluster: MiniCluster,
      env: StreamExecutionEnvironment,
      modelData: ModelData,
      process: CanonicalProcess,
      processVersion: ProcessVersion,
      deploymentData: DeploymentData,
      savepointPath: String
  ): Unit =
    new FlinkVerificationMain(
      miniCluster,
      env,
      modelData,
      process,
      processVersion,
      deploymentData,
      savepointPath
    ).runTest()

}

class FlinkVerificationMain(
    miniCluster: MiniCluster,
    env: StreamExecutionEnvironment,
    modelData: ModelData,
    process: CanonicalProcess,
    processVersion: ProcessVersion,
    deploymentData: DeploymentData,
    savepointPath: String
) {

  private val stubbedRunner = new FlinkStubbedRunner(miniCluster, env)

  def runTest(): Unit = {
    val collectingListener = ResultsCollectingListenerHolder.registerTestEngineListener
    val resultCollector    = new TestServiceInvocationCollector(collectingListener)
    val registrar          = prepareRegistrar()

    registrar.register(env, process, processVersion, deploymentData, resultCollector)
    stubbedRunner.execute(
      process.name,
      SavepointRestoreSettings.forPath(savepointPath, true),
      modelData.modelClassLoader
    )
  }

  protected def prepareRegistrar(): FlinkProcessRegistrar = {
    FlinkProcessRegistrar(
      VerificationFlinkProcessCompilerDataFactory(process, modelData),
      FlinkJobConfig.parse(modelData.modelConfig),
      ExecutionConfigPreparer.defaultChain(modelData)
    )
  }

}
