package pl.touk.nussknacker.engine.flink.minicluster.scenariotesting.legacyadhocminicluster

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.configuration.{Configuration, TaskManagerOptions}
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.flink.minicluster.{FlinkMiniClusterFactory, FlinkMiniClusterWithServices}
import pl.touk.nussknacker.engine.util.MetaDataExtractor

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader

// This class handles a legacy ad-hoc way to create minicluster.
// TODO: After we fully switch to shared mini cluster approach, it should be removed
class LegacyAdHocMiniClusterFallbackHandler(modelClassLoader: URLClassLoader, useCaseForDebug: String)
    extends LazyLogging {

  def handleAdHocMniClusterFallbackAsync[R](
      sharedMiniClusterServicesOpt: Option[FlinkMiniClusterWithServices],
      scenario: CanonicalProcess
  )(f: FlinkMiniClusterWithServices => Future[R])(implicit ec: ExecutionContext): Future[R] = {
    val (adHocMiniClusterOpt, miniClusterToUse) =
      useSharedMiniClusterOrAdHoc(sharedMiniClusterServicesOpt, scenario)
    val resultFuture = f(miniClusterToUse)
    resultFuture.onComplete(_ => adHocMiniClusterOpt.foreach(_.close()))
    resultFuture
  }

  def handleAdHocMniClusterFallback[R](
      sharedMiniClusterServicesOpt: Option[FlinkMiniClusterWithServices],
      scenario: CanonicalProcess
  )(f: FlinkMiniClusterWithServices => R): R = {
    val (adHocMiniClusterOpt, miniClusterToUse) =
      useSharedMiniClusterOrAdHoc(sharedMiniClusterServicesOpt, scenario)
    try {
      f(miniClusterToUse)
    } finally {
      adHocMiniClusterOpt.foreach(_.close())
    }
  }

  private def useSharedMiniClusterOrAdHoc[R](
      sharedMiniClusterServicesOpt: Option[FlinkMiniClusterWithServices],
      scenario: CanonicalProcess
  ): (Option[FlinkMiniClusterWithServices], FlinkMiniClusterWithServices) = {
    sharedMiniClusterServicesOpt
      .map { sharedMiniClusterServices =>
        logger.debug(s"Shared MiniCluster used for $useCaseForDebug")
        (None, sharedMiniClusterServices)
      }
      .getOrElse {
        logger.debug(s"Shared MiniCluster not used for $useCaseForDebug. Creating ad-hoc MiniCluster")
        val adHocMiniCluster = createAdHocMiniCluster(scenario)
        (Some(adHocMiniCluster), adHocMiniCluster)
      }
  }

  private def createAdHocMiniCluster(process: CanonicalProcess) = {
    val scenarioParallelism = MetaDataExtractor
      .extractTypeSpecificDataOrDefault[StreamMetaData](process.metaData, StreamMetaData())
      .parallelism
      .getOrElse(1)
    val legacyMiniClusterConfigOverrides = new Configuration
    legacyMiniClusterConfigOverrides.set[java.lang.Integer](TaskManagerOptions.NUM_TASK_SLOTS, scenarioParallelism)
    FlinkMiniClusterFactory.createMiniClusterWithServices(
      modelClassLoader,
      legacyMiniClusterConfigOverrides,
      new Configuration()
    )
  }

}
