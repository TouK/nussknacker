package pl.touk.nussknacker.engine.flink.minicluster.scenariotesting.legacysingleuseminicluster

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.configuration.{Configuration, TaskManagerOptions}
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.minicluster.{FlinkMiniClusterFactory, FlinkMiniClusterWithServices}
import pl.touk.nussknacker.engine.util.MetaDataExtractor

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader

// This class handles a legacy way to create single use minicluster
// TODO: After we fully switch to shared mini cluster approach, it should be removed
class LegacyFallbackToSingleUseMiniClusterHandler(modelClassLoader: URLClassLoader, useCaseForDebug: String)
    extends LazyLogging {

  def withSharedOrSingleUseClusterAsync[R](
      sharedMiniClusterServicesOpt: Option[FlinkMiniClusterWithServices],
      scenario: CanonicalProcess
  )(f: FlinkMiniClusterWithServices => Future[R])(implicit ec: ExecutionContext): Future[R] = {
    val (singleUseClusterOpt, miniClusterToUse) =
      useSharedOrSingleUseCluster(sharedMiniClusterServicesOpt, scenario)
    val resultFuture = f(miniClusterToUse)
    resultFuture.onComplete(_ => singleUseClusterOpt.foreach(_.close()))
    resultFuture
  }

  def withSharedOrSingleUseCluster[R](
      sharedMiniClusterServicesOpt: Option[FlinkMiniClusterWithServices],
      scenario: CanonicalProcess
  )(f: FlinkMiniClusterWithServices => R): R = {
    val (singleUseClusterOpt, miniClusterToUse) =
      useSharedOrSingleUseCluster(sharedMiniClusterServicesOpt, scenario)
    try {
      f(miniClusterToUse)
    } finally {
      singleUseClusterOpt.foreach(_.close())
    }
  }

  private def useSharedOrSingleUseCluster[R](
      sharedMiniClusterServicesOpt: Option[FlinkMiniClusterWithServices],
      scenario: CanonicalProcess
  ): (Option[FlinkMiniClusterWithServices], FlinkMiniClusterWithServices) = {
    sharedMiniClusterServicesOpt
      .map { sharedMiniClusterServices =>
        logger.debug(s"Shared MiniCluster used for $useCaseForDebug")
        (None, sharedMiniClusterServices)
      }
      .getOrElse {
        logger.debug(s"Shared MiniCluster not used for $useCaseForDebug. Creating single use MiniCluster")
        val singleUseCluster = createSingleUseMiniCluster(scenario)
        (Some(singleUseCluster), singleUseCluster)
      }
  }

  private def createSingleUseMiniCluster(process: CanonicalProcess) = {
    val scenarioParallelism = MetaDataExtractor
      .extractTypeSpecificDataOrDefault[StreamMetaData](process.metaData, StreamMetaData())
      .parallelism
      .getOrElse(1)
    val legacyMiniClusterConfigOverrides = new Configuration
    legacyMiniClusterConfigOverrides.set[java.lang.Integer](TaskManagerOptions.NUM_TASK_SLOTS, scenarioParallelism)
    FlinkMiniClusterFactory.createMiniClusterWithServices(
      modelClassLoader,
      legacyMiniClusterConfigOverrides,
    )
  }

}
