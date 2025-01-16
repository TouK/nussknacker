package pl.touk.nussknacker.engine.process.runner

import org.apache.flink.configuration._
import org.apache.flink.core.fs.FileSystem
import org.apache.flink.runtime.jobgraph.{JobGraph, SavepointRestoreSettings}
import org.apache.flink.runtime.minicluster.{MiniCluster, MiniClusterConfiguration}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader

import scala.jdk.CollectionConverters._
import scala.util.Using

final class FlinkStubbedRunner(modelClassLoader: ModelClassLoader, configuration: Configuration) {

  def createEnv(parallelism: Int): StreamExecutionEnvironment = {
    val env = StreamExecutionEnvironment.createLocalEnvironment(
      parallelism,
      configuration
    )
    // Checkpoints are disabled to prevent waiting for checkpoint to happen
    // before finishing execution.
    env.getCheckpointConfig.disableCheckpointing()
    env
  }

  private def createMiniCluster(configuration: Configuration, numSlotsPerTaskManager: Int) = {
    val miniCluster = new MiniCluster(
      new MiniClusterConfiguration.Builder()
        .setNumSlotsPerTaskManager(numSlotsPerTaskManager)
        .setConfiguration(configuration)
        .build()
    )
    miniCluster.start()
    miniCluster
  }

  // we use own LocalFlinkMiniCluster, instead of LocalExecutionEnvironment, to be able to pass own classpath...
  def execute(
      env: StreamExecutionEnvironment,
      parallelism: Int,
      scenarioName: ProcessName,
      savepointRestoreSettings: SavepointRestoreSettings
  ): Unit = {
    val streamGraph = env.getStreamGraph
    streamGraph.setJobName(scenarioName.value)
    val jobGraph = streamGraph.getJobGraph
    setupJobGraph(jobGraph, savepointRestoreSettings)

    val miniClusterConfiguration = prepareMiniClusterConfiguration(numTaskSlots = parallelism, jobGraph)

    // it is required for proper working of HadoopFileSystem
    FileSystem.initialize(miniClusterConfiguration, null)

    val miniCluster = createMiniCluster(miniClusterConfiguration, numSlotsPerTaskManager = parallelism)

    Using.resource(miniCluster) { miniCluster =>
      val id = miniCluster.submitJob(jobGraph).get().getJobID
      miniCluster.requestJobResult(id).get().toJobExecutionResult(getClass.getClassLoader)
    }
  }

  private def setupJobGraph(
      jobGraph: JobGraph,
      savepointRestoreSettings: SavepointRestoreSettings
  ): Unit = {
    jobGraph.setClasspaths(classpathsFromModelWithFallbackToConfiguration)
    jobGraph.setSavepointRestoreSettings(savepointRestoreSettings)
  }

  private def classpathsFromModelWithFallbackToConfiguration = {
    // The class is also used in some scala tests
    // and this fallback is to work with a work around for a behaviour added in https://issues.apache.org/jira/browse/FLINK-32265
    // see details in pl.touk.nussknacker.engine.flink.test.MiniClusterExecutionEnvironment#execute
    modelClassLoader.urls match {
      // FIXME abr: is it necessary?
//      case Nil =>
//        ConfigUtils.decodeListFromConfig[String, URL, MalformedURLException](
//          configuration,
//          PipelineOptions.CLASSPATHS,
//          new URL(_)
//        )
      case list => list.asJava
    }
  }

  private def prepareMiniClusterConfiguration(numTaskSlots: Int, jobGraph: JobGraph) = {
    val configuration: Configuration = new Configuration
    configuration.addAll(jobGraph.getJobConfiguration)
    configuration.set[Integer](TaskManagerOptions.NUM_TASK_SLOTS, numTaskSlots)
    configuration.set[Integer](RestOptions.PORT, 0)

    // FIXME: reversing flink default order
    configuration.set(CoreOptions.CLASSLOADER_RESOLVE_ORDER, "parent-first")
    configuration
  }

}
