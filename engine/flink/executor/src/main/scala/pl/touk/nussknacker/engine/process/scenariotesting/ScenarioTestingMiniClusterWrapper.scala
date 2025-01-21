package pl.touk.nussknacker.engine.process.scenariotesting

import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.jobgraph.{JobGraph, SavepointRestoreSettings}
import org.apache.flink.runtime.minicluster.MiniCluster
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.util.MetaDataExtractor
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader

import scala.jdk.CollectionConverters._

// We use MiniCluster directly, instead of LocalExecutionEnvironment, to be able to pass own classpath...
final class ScenarioTestingMiniClusterWrapper(
    miniCluster: MiniCluster,
    val env: StreamExecutionEnvironment,
    parallelism: Int
) extends AutoCloseable {

  def alignParallelism(canonicalProcess: CanonicalProcess): CanonicalProcess = {
    val scenarioParallelism = MetaDataExtractor
      .extractTypeSpecificDataOrDefault[StreamMetaData](canonicalProcess.metaData, StreamMetaData())
      .parallelism
    if (scenarioParallelism.exists(_ > parallelism)) {
      canonicalProcess.copy(metaData =
        canonicalProcess.metaData.copy(additionalFields =
          canonicalProcess.metaData.additionalFields.copy(properties =
            canonicalProcess.metaData.additionalFields.properties + (StreamMetaData.parallelismName -> parallelism.toString)
          )
        )
      )
    } else {
      canonicalProcess
    }
  }

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

  def close(): Unit = {
    env.close()
    miniCluster.close()
  }

}

object ScenarioTestingMiniClusterWrapper {

  def create(parallelism: Int, streamExecutionConfig: Configuration): ScenarioTestingMiniClusterWrapper = {
    val miniCluster = ScenarioTestingMiniClusterFactory.createConfiguredMiniCluster(parallelism)
    val env = ScenarioTestingStreamExecutionEnvironmentFactory.createStreamExecutionEnvironment(
      parallelism,
      streamExecutionConfig
    )
    new ScenarioTestingMiniClusterWrapper(miniCluster, env, parallelism)
  }

}
