package pl.touk.nussknacker.engine.process.runner

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{TestData, TestResults}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.TestFlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, ResultsCollectingListenerHolder}

object FlinkTestMain extends FlinkRunner {

  def run[T](modelData: ModelData, processJson: String, testData: TestData, configuration: Configuration, variableEncoder: Any => T): TestResults[T] = {
    val process = readProcessFromArg(processJson)
    val processVersion = ProcessVersion.empty.copy(processName = ProcessName("snapshot version")) // testing process may be unreleased, so it has no version
    new FlinkTestMain(modelData, process, testData, processVersion, DeploymentData.empty, configuration).runTest(variableEncoder)
  }
}

class FlinkTestMain(val modelData: ModelData,
                    val process: EspProcess,
                    testData: TestData,
                    processVersion: ProcessVersion,
                    deploymentData: DeploymentData,
                    val configuration: Configuration)
  extends FlinkStubbedRunner {

  def runTest[T](variableEncoder: Any => T): TestResults[T] = {
    val env = createEnv
    val collectingListener = ResultsCollectingListenerHolder.registerRun(variableEncoder)
    try {
      val registrar: FlinkProcessRegistrar = prepareRegistrar(env.getConfig, collectingListener, testData)
      registrar.register(env, process, processVersion, deploymentData)
      execute(env, SavepointRestoreSettings.none())
      collectingListener.results
    } finally {
      collectingListener.clean()
    }
  }

  protected def prepareRegistrar[T](config: ExecutionConfig,
                                    collectingListener: ResultsCollectingListener,
                                    testData: TestData): FlinkProcessRegistrar = {
    FlinkProcessRegistrar(new TestFlinkProcessCompiler(
      modelData.configCreator,
      modelData.processConfig,
      collectingListener,
      process,
      testData,
      config,
      modelData.objectNaming),
      ExecutionConfigPreparer.defaultChain(modelData))
  }
}

