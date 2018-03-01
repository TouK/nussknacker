package pl.touk.nussknacker.engine.process.runner

import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment.test.{TestData, TestResults}
import pl.touk.nussknacker.engine.api.test.ResultsCollectingListenerHolder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.process.compiler.TestFlinkProcessCompiler

object FlinkTestMain extends FlinkRunner {

  def run[T](modelData: ModelData, processJson: String, testData: TestData, configuration: Configuration, variableEncoder: Any => T): TestResults[T] = {
    val process = readProcessFromArg(processJson)
    new FlinkTestMain(modelData, process, testData, configuration).runTest(variableEncoder)
  }
}

case class FlinkTestMain(modelData: ModelData, process: EspProcess, testData: TestData,
                         configuration: Configuration)
  extends FlinkStubbedRunner {

  def runTest[T](variableEncoder: Any => T): TestResults[T] = {
    val env = createEnv
    val collectingListener = ResultsCollectingListenerHolder.registerRun(variableEncoder)
    try {
      val registrar: FlinkProcessRegistrar = new TestFlinkProcessCompiler(
        modelData.configCreator,
        modelData.processConfig,
        collectingListener,
        process,
        testData, env.getConfig).createFlinkProcessRegistrar()
      registrar.register(env, process, Option(collectingListener.runId))
      execute(env, SavepointRestoreSettings.none())
      collectingListener.results
    } finally {
      collectingListener.clean()
    }
  }
}

