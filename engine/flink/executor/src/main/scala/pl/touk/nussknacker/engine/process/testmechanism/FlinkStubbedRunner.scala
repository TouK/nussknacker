package pl.touk.nussknacker.engine.process.testmechanism

import org.apache.flink.runtime.jobgraph.{JobGraph, SavepointRestoreSettings}
import org.apache.flink.runtime.minicluster.MiniCluster
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader

import scala.jdk.CollectionConverters._

// FIXME abr: rename
// we use own LocalFlinkMiniCluster, instead of LocalExecutionEnvironment, to be able to pass own classpath...
final class FlinkStubbedRunner(miniCluster: MiniCluster, env: StreamExecutionEnvironment) {

  def execute(
      scenarioName: ProcessName,
      savepointRestoreSettings: SavepointRestoreSettings,
      modelClassLoader: ModelClassLoader
  ): Unit = {
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
