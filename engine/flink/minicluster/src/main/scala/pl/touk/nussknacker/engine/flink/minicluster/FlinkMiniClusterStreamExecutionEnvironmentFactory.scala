package pl.touk.nussknacker.engine.flink.minicluster

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.{JobID, JobSubmissionResult}
import org.apache.flink.api.dag.Pipeline
import org.apache.flink.configuration.{Configuration, DeploymentOptions, PipelineOptions, PipelineOptionsInternal}
import org.apache.flink.core.execution.{PipelineExecutor, PipelineExecutorFactory, PipelineExecutorServiceLoader}
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import org.apache.flink.runtime.minicluster.{MiniCluster, MiniClusterJobClient}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.graph.StreamGraph
import pl.touk.nussknacker.engine.classloader.ModelClassLoader

import java.lang.{Boolean => JBoolean}
import java.util.{stream => jstream}
import scala.jdk.CollectionConverters._
import scala.util.Try

object FlinkMiniClusterStreamExecutionEnvironmentFactory extends LazyLogging {

  private val pipelineExecutorName = "minicluster"

  def createStreamExecutionEnvironment(
      miniCluster: MiniCluster,
      modelClassLoader: ModelClassLoader,
      attached: Boolean
  ): StreamExecutionEnvironment = {
    val pipelineExecutorServiceLoader = createPipelineExecutorServiceLoader(miniCluster, modelClassLoader)
    val configuration                 = new Configuration()
    configuration.set(DeploymentOptions.TARGET, pipelineExecutorName)
    configuration.set(PipelineOptions.CLASSPATHS, modelClassLoader.getURLs.map(_.toString).toList.asJava)
    configuration.set[java.lang.Boolean](DeploymentOptions.ATTACHED, attached)
    new StreamExecutionEnvironment(pipelineExecutorServiceLoader, configuration, modelClassLoader)
  }

  // This is a copy-paste of MiniClusterPipelineExecutorServiceLoader. We don't use it directly because
  // we don't want to add dependency to flink-test-utils.
  // Also, PipelineExecutorUtils.getJobGraph is replaced with simple streamGraph.getJobGraph to avoid adding
  // dependency to flink-clients.
  private def createPipelineExecutorServiceLoader(
      miniCluster: MiniCluster,
      modelClassLoader: ModelClassLoader
  ): PipelineExecutorServiceLoader =
    new PipelineExecutorServiceLoader {

      override def getExecutorFactory(configuration: Configuration): PipelineExecutorFactory =
        new PipelineExecutorFactory {
          override def getName: String = pipelineExecutorName

          override def isCompatibleWith(configuration: Configuration): Boolean = true

          override def getExecutor(configuration: Configuration): PipelineExecutor =
            (pipeline: Pipeline, _: Configuration, userCodeClassloader: ClassLoader) => {
              pipeline match {
                case streamGraph: StreamGraph =>
                  Option(configuration.get(PipelineOptionsInternal.PIPELINE_FIXED_JOB_ID))
                    .map(JobID.fromHexString)
                    .foreach(streamGraph.setJobId)

                  ensureCorrectClassloaderForExtendedDebugInfo(modelClassLoader)

                  streamGraph.setClasspath(modelClassLoader.getURLs.toList.asJava)
                  if (streamGraph.getSavepointRestoreSettings == SavepointRestoreSettings.none)
                    streamGraph.setSavepointRestoreSettings(streamGraph.getSavepointRestoreSettings)

                  miniCluster
                    .submitJob(streamGraph)
                    .thenApply((result: JobSubmissionResult) =>
                      new MiniClusterJobClient(
                        result.getJobID,
                        miniCluster,
                        userCodeClassloader,
                        MiniClusterJobClient.JobFinalizationBehavior.NOTHING
                      )
                    )
                case other =>
                  throw new IllegalArgumentException(s"Unsupported pipeline type: $other")
              }
            }

        }

      override def getExecutorNames: jstream.Stream[String] = List(pipelineExecutorName).asJava.stream()
    }

  private val extendedDebugInfoPropertyName = "sun.io.serialization.extendedDebugInfo"

  // When running tests, sometimes StreamConfig is loaded with AppClassLoader or sbt.internal.LayeredClassLoader instead of ModelClassLoader
  // It causes ClassCastException because in StreamConfig.toString is used this.getClass.getClassloader
  private def ensureCorrectClassloaderForExtendedDebugInfo(modelClassLoader: ModelClassLoader): Unit = {
    logger.whenWarnEnabled {
      Try(Class.forName("org.apache.flink.streaming.api.graph.StreamConfig", true, modelClassLoader)).foreach {
        streamConfigClass =>
          if (JBoolean.getBoolean(
              extendedDebugInfoPropertyName
            ) && streamConfigClass.getClassLoader != modelClassLoader) {
            logger.warn(
              s"You have enabled [$extendedDebugInfoPropertyName] property in a run environment where [${streamConfigClass.getName}] " +
                s"class was loaded with other classloader (${streamConfigClass.getClassLoader}) than model class loader. " +
                s"It may cause ${classOf[ClassCastException].getSimpleName} errors. To avoid this problem, disable this flag " +
                s"or run job in other environment"
            )
          }
      }
    }
  }

}
