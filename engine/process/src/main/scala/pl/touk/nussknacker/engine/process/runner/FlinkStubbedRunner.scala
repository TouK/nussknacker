package pl.touk.nussknacker.engine.process.runner

import java.net.URL

import org.apache.flink.api.common.JobExecutionResult
import org.apache.flink.configuration.{ConfigConstants, Configuration}
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import org.apache.flink.runtime.minicluster.LocalFlinkMiniCluster
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.util.MetaDataExtractor

import scala.collection.JavaConverters._

trait FlinkStubbedRunner {

  protected def modelData: ModelData

  protected def process: EspProcess

  protected def createEnv : StreamExecutionEnvironment =
    StreamExecutionEnvironment.createLocalEnvironment(MetaDataExtractor.extractStreamMetaDataOrFail(process.metaData).parallelism.getOrElse(1))

  //we use own LocalFlinkMiniCluster, instead of LocalExecutionEnvironment, to be able to pass own classpath...
  protected def execute(env: StreamExecutionEnvironment, savepointRestoreSettings: SavepointRestoreSettings) : JobExecutionResult = {
    val streamGraph = env.getStreamGraph
    streamGraph.setJobName(process.id)

    val jobGraph = streamGraph.getJobGraph
    jobGraph.setClasspaths(modelData.jarClassLoader.urls.asJava)
    jobGraph.setSavepointRestoreSettings(savepointRestoreSettings)

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
