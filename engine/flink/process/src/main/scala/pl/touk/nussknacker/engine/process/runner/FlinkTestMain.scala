package pl.touk.nussknacker.engine.process.runner

import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.{JobData, ProcessVersion}
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{TestData, TestResults}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.test.ResultsCollectingListenerHolder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.FlinkStreamingProcessRegistrar
import pl.touk.nussknacker.engine.process.compiler.TestFlinkProcessCompiler

object FlinkTestMain extends FlinkRunner {

  def run[T](modelData: ModelData, processJson: String, testData: TestData, configuration: Configuration, variableEncoder: Any => T): TestResults[T] = {
    val process = readProcessFromArg(processJson)
    val processVersion = ProcessVersion.empty.copy(processName = ProcessName("snapshot version")) // testing process may be unreleased, so it has no version
    new FlinkTestMain(modelData, process, testData, processVersion, configuration).runTest(variableEncoder)
  }
}

case class FlinkTestMain(modelData: ModelData, process: EspProcess, testData: TestData, processVersion: ProcessVersion,
                         configuration: Configuration)
  extends FlinkStubbedRunner {

  def runTest[T](variableEncoder: Any => T): TestResults[T] = {
    val env = createEnv
    val collectingListener = ResultsCollectingListenerHolder.registerRun(variableEncoder)
    try {
      val registrar: FlinkStreamingProcessRegistrar = FlinkStreamingProcessRegistrar(new TestFlinkProcessCompiler(
        modelData.configCreator,
        modelData.processConfigFromConfiguration,
        collectingListener,
        process,
        testData, env.getConfig), modelData.processConfig)
      registrar.register(env, process, processVersion, Option(collectingListener.runId))
      execute(env, SavepointRestoreSettings.none())
      collectingListener.results
    } finally {
      collectingListener.clean()
    }
  }
}

