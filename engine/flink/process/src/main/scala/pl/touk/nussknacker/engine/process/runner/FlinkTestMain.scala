package pl.touk.nussknacker.engine.process.runner

import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{TestData, TestResults}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.test.{ResultsCollectingListener, ResultsCollectingListenerHolder}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.{ExecutionConfigPreparer, registrar}
import pl.touk.nussknacker.engine.process.compiler.TestFlinkProcessCompiler
import pl.touk.nussknacker.engine.process.registrar.FlinkProcessRegistrar

object FlinkTestMain extends FlinkRunner {

  def run[T](modelData: ModelData, processJson: String, testData: TestData, configuration: Configuration, variableEncoder: Any => T): TestResults[T] = {
    val process = readProcessFromArg(processJson)
    val processVersion = ProcessVersion.empty.copy(processName = ProcessName("snapshot version")) // testing process may be unreleased, so it has no version
    new FlinkTestMain(modelData, process, testData, processVersion, configuration).runTest(variableEncoder)
  }
}

class FlinkTestMain(val modelData: ModelData, val process: EspProcess, testData: TestData, processVersion: ProcessVersion,
                    val configuration: Configuration)
  extends FlinkStubbedRunner {

  def runTest[T](variableEncoder: Any => T): TestResults[T] = {
    val env = createEnv
    val collectingListener = ResultsCollectingListenerHolder.registerRun(variableEncoder)
    try {
      val registrar: FlinkProcessRegistrar = prepareRegistrar(env, collectingListener)
      registrar.register(env, process, processVersion, Option(collectingListener.runId))
      execute(env, SavepointRestoreSettings.none())
      collectingListener.results
    } finally {
      collectingListener.clean()
    }
  }

  protected def prepareRegistrar[T](env: StreamExecutionEnvironment, collectingListener: ResultsCollectingListener): FlinkProcessRegistrar = {
    FlinkProcessRegistrar(new TestFlinkProcessCompiler(
      modelData.configCreator,
      modelData.inputConfig,
      modelData.modelConfigLoader,
      collectingListener,
      process,
      testData,
      env.getConfig,
      modelData.objectNaming),
      modelData.processConfig,
      ExecutionConfigPreparer.defaultChain(modelData))
  }
}

