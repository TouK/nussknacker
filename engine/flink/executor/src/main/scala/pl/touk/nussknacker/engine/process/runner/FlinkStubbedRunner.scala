package pl.touk.nussknacker.engine.process.runner

import org.apache.flink.configuration.{Configuration, CoreOptions, RestOptions, TaskManagerOptions}
import org.apache.flink.core.fs.FileSystem
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import org.apache.flink.runtime.minicluster.{MiniCluster, MiniClusterConfiguration}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.util.MetaDataExtractor

import java.net.URL
import scala.jdk.CollectionConverters._
import scala.util.Using

trait FlinkStubbedRunner {

  protected def modelData: ModelData

  protected def process: CanonicalProcess

  protected def configuration: Configuration

  protected def createEnv: StreamExecutionEnvironment = StreamExecutionEnvironment.createLocalEnvironment(
    MetaDataExtractor
      .extractTypeSpecificDataOrDefault[StreamMetaData](process.metaData, StreamMetaData())
      .parallelism
      .getOrElse(1),
    configuration
  )

  // we use own LocalFlinkMiniCluster, instead of LocalExecutionEnvironment, to be able to pass own classpath...
  protected def execute[T](
      env: StreamExecutionEnvironment,
      savepointRestoreSettings: SavepointRestoreSettings
  ): Unit = {
    // Checkpoints are disabled to prevent waiting for checkpoint to happen
    // before finishing execution.
    env.getCheckpointConfig.disableCheckpointing()

    val streamGraph = env.getStreamGraph
    streamGraph.setJobName(process.name.value)

    val jobGraph = streamGraph.getJobGraph()
    val urls     = modelClasspathWithFallbackToDummyEntry
    jobGraph.setClasspaths(urls.asJava)
    jobGraph.setSavepointRestoreSettings(savepointRestoreSettings)

    val configuration: Configuration = new Configuration
    configuration.addAll(jobGraph.getJobConfiguration)
    configuration.setInteger(TaskManagerOptions.NUM_TASK_SLOTS, env.getParallelism)
    configuration.setInteger(RestOptions.PORT, 0)

    // FIXME: reversing flink default order
    configuration.setString(CoreOptions.CLASSLOADER_RESOLVE_ORDER, "parent-first")

    // it is required for proper working of HadoopFileSystem
    FileSystem.initialize(configuration, null)

    Using.resource(
      new MiniCluster(
        new MiniClusterConfiguration.Builder()
          .setNumSlotsPerTaskManager(env.getParallelism)
          .setConfiguration(configuration)
          .build()
      )
    ) { exec =>
      exec.start()
      val id = exec.submitJob(jobGraph).get().getJobID
      exec.requestJobResult(id).get().toJobExecutionResult(getClass.getClassLoader)
    }
  }

  private def modelClasspathWithFallbackToDummyEntry = {
    modelData.modelClassLoaderUrls match {
      case Nil =>
        // The class is also used in some scala tests
        // and this is a work around for a behaviour added in https://issues.apache.org/jira/browse/FLINK-32265
        // see details in pl.touk.nussknacker.engine.flink.test.MiniClusterExecutionEnvironment#execute
        List(new URL("http://dummy-classpath.invalid"))
      case list => list
    }
  }

}
