package pl.touk.nussknacker.engine.process.testmechanism

import org.apache.flink.runtime.jobgraph.{JobGraph, SavepointRestoreSettings}
import org.apache.flink.runtime.minicluster.MiniCluster
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader

import scala.jdk.CollectionConverters._

// We use MiniCluster directly, instead of LocalExecutionEnvironment, to be able to pass own classpath...
final class FlinkMiniClusterJobSubmitter(miniCluster: MiniCluster, env: StreamExecutionEnvironment) {

  def submitJobAndCleanEnv(
      scenarioName: ProcessName,
      savepointRestoreSettings: SavepointRestoreSettings,
      modelClassLoader: ModelClassLoader
  ): Unit = {
    // This step clean env transformations. It allows to reuse the same StreamExecutionEnvironment many times
    val streamGraph = env.getStreamGraph
    streamGraph.setJobName(scenarioName.value)
    val jobGraph = streamGraph.getJobGraph
    setupJobGraph(jobGraph, savepointRestoreSettings, modelClassLoader)

    val id = miniCluster.submitJob(jobGraph).get().getJobID
    miniCluster.requestJobResult(id).get().toJobExecutionResult(getClass.getClassLoader)
  }

  private def setupJobGraph(
      jobGraph: JobGraph,
      savepointRestoreSettings: SavepointRestoreSettings,
      modelClassLoader: ModelClassLoader
  ): Unit = {
    jobGraph.setClasspaths(modelClassLoader.urls.asJava)
    jobGraph.setSavepointRestoreSettings(savepointRestoreSettings)
  }

}
