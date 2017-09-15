package pl.touk.nussknacker.engine.process.runner

import java.net.URL

import com.typesafe.config.Config
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import pl.touk.nussknacker.engine.api.deployment.test.{TestData, TestResults}
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.api.test.ResultsCollectingListenerHolder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.process.compiler.TestFlinkProcessCompiler

object FlinkTestMain extends FlinkRunner {

  def run(processJson: String, config: Config, testData: TestData, urls: List[URL]): TestResults = {
    val process = readProcessFromArg(processJson)
    val creator: ProcessConfigCreator = loadCreator

    new FlinkTestMain(config, testData, urls, process, creator).runTest()
  }
}

class FlinkTestMain(config: Config, testData: TestData,
                    val urls: List[URL],
                    val process: EspProcess, creator: ProcessConfigCreator)
  extends Serializable with FlinkStubbedRunner {

  def runTest(): TestResults = {
    val env = createEnv
    val collectingListener = ResultsCollectingListenerHolder.registerRun
    try {
      val registrar: FlinkProcessRegistrar = new TestFlinkProcessCompiler(
        creator,
        config,
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

