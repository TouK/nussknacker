package pl.touk.nussknacker.engine.process.runner

import java.net.URL

import com.typesafe.config.Config
import org.apache.flink.api.common.restartstrategy.RestartStrategies.NoRestartStrategyConfiguration
import org.apache.flink.configuration.{ConfigConstants, Configuration}
import org.apache.flink.runtime.minicluster.LocalFlinkMiniCluster
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.deployment.test.{TestData, TestResults}
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.api.test.ResultsCollectingListenerHolder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.FlinkProcessRegistrar
import pl.touk.nussknacker.engine.process.compiler.TestFlinkProcessCompiler
import pl.touk.nussknacker.engine.process.util.MetaDataExtractor

import scala.collection.JavaConverters._

object FlinkTestMain extends FlinkRunner {

  def run(processJson: String, config: Config, testData: TestData, urls: List[URL]): TestResults = {
    val process = readProcessFromArg(processJson)
    val creator: ProcessConfigCreator = loadCreator(config)

    new FlinkTestMain(config, testData, process, creator).runTest(urls)
  }
}

class FlinkTestMain(config: Config, testData: TestData, process: EspProcess, creator: ProcessConfigCreator) extends Serializable {

  private def overWriteRestartStrategy(env: StreamExecutionEnvironment) = env.setRestartStrategy(new NoRestartStrategyConfiguration)

  def runTest(urls: List[URL]): TestResults = {
    val env = StreamExecutionEnvironment.createLocalEnvironment(MetaDataExtractor.extractStreamMetaDataOrFail(process.metaData).parallelism.getOrElse(1))
    val collectingListener = ResultsCollectingListenerHolder.registerRun
    try {
      val registrar: FlinkProcessRegistrar = new TestFlinkProcessCompiler(
        creator,
        config,
        collectingListener,
        process,
        testData, env.getConfig).createFlinkProcessRegistrar()
      registrar.register(env, process, Option(collectingListener.runId))
      overWriteRestartStrategy(env)
      execute(env, urls)
      collectingListener.results
    } finally {
      collectingListener.clean()
    }
  }

  //we use own LocalFlinkMiniCluster, instead of LocalExecutionEnvironment, to be able to pass own classpath...
  private def execute(env: StreamExecutionEnvironment, urls: List[URL]) = {
    val streamGraph = env.getStreamGraph
    streamGraph.setJobName(process.id)

    val jobGraph = streamGraph.getJobGraph
    jobGraph.setClasspaths(urls.asJava)

    val configuration: Configuration = new Configuration
    configuration.addAll(jobGraph.getJobConfiguration)
    configuration.setInteger(ConfigConstants.TASK_MANAGER_NUM_TASK_SLOTS, env.getParallelism)

    val exec: LocalFlinkMiniCluster = new LocalFlinkMiniCluster(configuration, true)
    try {
      exec.start()
      exec.submitJobAndWait(jobGraph, printUpdates = false)
    } finally {
      exec.stop()
    }
  }

}

