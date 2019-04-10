package pl.touk.nussknacker.engine.process.runner

import org.apache.flink.api.common.JobExecutionResult
import org.apache.flink.core.fs.FileSystem
import org.apache.flink.configuration.{ConfigConstants, Configuration, CoreOptions, TaskManagerOptions}
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

  protected def configuration: Configuration

  protected def createEnv : StreamExecutionEnvironment =
    StreamExecutionEnvironment.createLocalEnvironment(
      MetaDataExtractor.extractStreamMetaDataOrFail(process.metaData).parallelism.getOrElse(1), configuration)

  //we use own LocalFlinkMiniCluster, instead of LocalExecutionEnvironment, to be able to pass own classpath...
  protected def execute[T](env: StreamExecutionEnvironment, savepointRestoreSettings: SavepointRestoreSettings) : JobExecutionResult = {
    val streamGraph = env.getStreamGraph
    streamGraph.setJobName(process.id)

    val jobGraph = streamGraph.getJobGraph(null)
    jobGraph.setClasspaths(modelData.modelClassLoader.urls.asJava)
    jobGraph.setSavepointRestoreSettings(savepointRestoreSettings)

    val configuration: Configuration = new Configuration
    configuration.addAll(jobGraph.getJobConfiguration)
    configuration.setInteger(TaskManagerOptions.NUM_TASK_SLOTS, env.getParallelism)
    //FIXME: reversing flink default order
    configuration.setString(CoreOptions.CLASSLOADER_RESOLVE_ORDER, "parent-first")

    // it is required for proper working of HadoopFileSystem
    FileSystem.initialize(configuration)

    val exec: LocalFlinkMiniCluster = new LocalFlinkMiniCluster(configuration, true)
    try {
      exec.start()
      exec.submitJobAndWait(jobGraph, printUpdates = false)
    } finally {

      exec.stop()
    }
  }

}
