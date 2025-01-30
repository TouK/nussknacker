package pl.touk.nussknacker.engine.process.scenariotesting.legacyadhocminicluster

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.configuration.{Configuration, TaskManagerOptions}
import org.apache.flink.runtime.minicluster.MiniCluster
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.process.scenariotesting.StreamExecutionEnvironmentWithParallelismOverride
import pl.touk.nussknacker.engine.util.MetaDataExtractor
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader

import scala.concurrent.{ExecutionContext, Future}

// This class handles a legacy ad-hoc way to create minicluster.
// TODO: After we fully switch to shared mini cluster approach, it should be removed
class LegacyAdHocMiniClusterFallbackHandler(modelClassLoader: ModelClassLoader, useCaseForDebug: String)
    extends LazyLogging {

  def handleAdHocMniClusterFallbackAsync[R](
      sharedMiniClusterServicesOpt: Option[StreamExecutionEnvironmentWithParallelismOverride],
      scenario: CanonicalProcess
  )(f: StreamExecutionEnvironmentWithParallelismOverride => Future[R])(implicit ec: ExecutionContext): Future[R] = {
    val (allocatedMiniClusterResourcesOpt, streamEnvWithMaxParallelism) =
      useSharedMiniClusterOrAdHoc(sharedMiniClusterServicesOpt, scenario)
    val resultFuture = f(streamEnvWithMaxParallelism)
    resultFuture.onComplete(_ => allocatedMiniClusterResourcesOpt.foreach(_.close()))
    resultFuture
  }

  def handleAdHocMniClusterFallback[R](
      sharedMiniClusterServicesOpt: Option[StreamExecutionEnvironmentWithParallelismOverride],
      scenario: CanonicalProcess
  )(f: StreamExecutionEnvironmentWithParallelismOverride => R): R = {
    val (allocatedMiniClusterResourcesOpt, streamEnvWithMaxParallelism) =
      useSharedMiniClusterOrAdHoc(sharedMiniClusterServicesOpt, scenario)
    try {
      f(streamEnvWithMaxParallelism)
    } finally {
      allocatedMiniClusterResourcesOpt.foreach(_.close())
    }
  }

  private def useSharedMiniClusterOrAdHoc[R](
      sharedMiniClusterServicesOpt: Option[StreamExecutionEnvironmentWithParallelismOverride],
      scenario: CanonicalProcess
  ): (Option[AdHocMiniClusterResources], StreamExecutionEnvironmentWithParallelismOverride) = {
    sharedMiniClusterServicesOpt
      .map { sharedMiniClusterServices =>
        logger.debug(s"Shared MiniCluster used for $useCaseForDebug")
        (None, sharedMiniClusterServices)
      }
      .getOrElse {
        logger.debug(s"Shared MiniCluster not used for $useCaseForDebug. Creating ad-hoc MiniCluster")
        val resources = createAdHocMiniClusterResources(scenario)
        (Some(resources), StreamExecutionEnvironmentWithParallelismOverride.withoutParallelismOverriding(resources.env))
      }
  }

  private def createAdHocMiniClusterResources(process: CanonicalProcess) = {
    val scenarioParallelism = MetaDataExtractor
      .extractTypeSpecificDataOrDefault[StreamMetaData](process.metaData, StreamMetaData())
      .parallelism
      .getOrElse(1)
    val legacyMiniClusterConfigOverrides = new Configuration
    legacyMiniClusterConfigOverrides.set[java.lang.Integer](TaskManagerOptions.NUM_TASK_SLOTS, scenarioParallelism)
    val (miniCluster, createStreamExecutionEnvironment) =
      FlinkMiniClusterFactoryReflectiveInvoker.createMiniClusterWithServicesRaw(
        modelClassLoader,
        legacyMiniClusterConfigOverrides,
        new Configuration()
      )
    AdHocMiniClusterResources(miniCluster, createStreamExecutionEnvironment(true))
  }

  private case class AdHocMiniClusterResources(miniCluster: MiniCluster, env: StreamExecutionEnvironment)
      extends AutoCloseable {

    override def close(): Unit = {
      env.close()
      miniCluster.close()
    }

  }

}
