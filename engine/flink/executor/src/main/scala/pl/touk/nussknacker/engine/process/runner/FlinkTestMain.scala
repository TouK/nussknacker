package pl.touk.nussknacker.engine.process.runner

import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.test.{ScenarioTestData, TestData}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.process.ExecutionConfigPreparer
import pl.touk.nussknacker.engine.process.compiler.TestFlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, ResultsCollectingListenerHolder}

object FlinkTestMain extends FlinkRunner {

  def run[T](modelData: ModelData, process: CanonicalProcess, testData: ScenarioTestData, configuration: Configuration, variableEncoder: Any => T): TestResults[T] = {
    val processVersion = ProcessVersion.empty.copy(processName = ProcessName("snapshot version")) // testing process may be unreleased, so it has no version
    new FlinkTestMain(modelData, process, testData, processVersion, DeploymentData.empty, configuration).runTest(variableEncoder)
  }

}

class FlinkTestMain(val modelData: ModelData,
                    val process: CanonicalProcess,
                    testData: ScenarioTestData,
                    processVersion: ProcessVersion,
                    deploymentData: DeploymentData,
                    val configuration: Configuration)
  extends FlinkStubbedRunner {

  def runTest[T](variableEncoder: Any => T): TestResults[T] = {
    val env = createEnv
    val collectingListener = ResultsCollectingListenerHolder.registerRun(variableEncoder)
    try {
      val registrar: FlinkProcessRegistrar = prepareRegistrar(collectingListener, testData)
      registrar.register(env, process, processVersion, deploymentData, Option(collectingListener.runId))
      execute(env, SavepointRestoreSettings.none())
      collectingListener.results
    } finally {
      collectingListener.clean()
    }
  }

  protected def prepareRegistrar[T](collectingListener: ResultsCollectingListener, testData: ScenarioTestData): FlinkProcessRegistrar = {
    FlinkProcessRegistrar(new TestFlinkProcessCompiler(
      modelData.configCreator,
      modelData.processConfig,
      collectingListener,
      process,
      testData,
      modelData.objectNaming),
      ExecutionConfigPreparer.defaultChain(modelData))
  }
}

