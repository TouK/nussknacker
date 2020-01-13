package pl.touk.nussknacker.engine.process.runner

import org.apache.flink.api.common.JobExecutionResult
import org.apache.flink.core.fs.FileSystem
import org.apache.flink.configuration.{Configuration, CoreOptions, JobManagerOptions, RestOptions, TaskManagerOptions}
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import org.apache.flink.runtime.minicluster.{MiniCluster, MiniClusterConfiguration}
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.util.MetaDataExtractor

import scala.collection.JavaConverters._

trait FlinkStubbedRunner {

  protected def modelData: ModelData

  protected def process: EspProcess

  protected def configuration: Configuration

  protected def createEnv : StreamExecutionEnvironment =
    StreamExecutionEnvironment.createLocalEnvironment(
      MetaDataExtractor.extractTypeSpecificDataOrFail[StreamMetaData](process.metaData).parallelism.getOrElse(1), configuration)

  //we use own LocalFlinkMiniCluster, instead of LocalExecutionEnvironment, to be able to pass own classpath...
  protected def execute[T](env: StreamExecutionEnvironment, savepointRestoreSettings: SavepointRestoreSettings) : Unit = {
    val streamGraph = env.getStreamGraph
    streamGraph.setJobName(process.id)

    val jobGraph = streamGraph.getJobGraph(null)
    jobGraph.setClasspaths(modelData.modelClassLoader.urls.asJava)
    jobGraph.setSavepointRestoreSettings(savepointRestoreSettings)

    val configuration: Configuration = new Configuration
    configuration.addAll(jobGraph.getJobConfiguration)
    configuration.setInteger(TaskManagerOptions.NUM_TASK_SLOTS, env.getParallelism)
    configuration.setInteger(RestOptions.PORT, 0)

    //FIXME: reversing flink default order
    configuration.setString(CoreOptions.CLASSLOADER_RESOLVE_ORDER, "parent-first")

    // it is required for proper working of HadoopFileSystem
    FileSystem.initialize(configuration, null)

    val exec: MiniCluster = new MiniCluster(new MiniClusterConfiguration.Builder()
          .setNumSlotsPerTaskManager(env.getParallelism)
          .setConfiguration(configuration).build())
    try {
      exec.start()
      val id = exec.submitJob(jobGraph).get().getJobID
      exec.requestJobResult(id).get().toJobExecutionResult(getClass.getClassLoader)
    } finally {
      exec.close()
    }
  }

}
