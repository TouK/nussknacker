package pl.touk.nussknacker.engine.process.scenariotesting

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.util.MetaDataExtractor

// This class handles a legacy ad-hoc way to create minicluster.
// After we fully switch to single mini cluster approach, it should be removed
object AdHocMiniClusterFallbackHandler extends LazyLogging {

  def handleAdHocMniClusterFallback[R](
      reusableMiniClusterWrapperOpt: Option[ScenarioTestingMiniClusterWrapper],
      scenario: CanonicalProcess,
      useCaseForDebug: String
  )(f: ScenarioTestingMiniClusterWrapper => R): R = {
    val miniClusterWrapper = reusableMiniClusterWrapperOpt
      .map { reusableMiniClusterWrapper =>
        logger.debug(s"reusableMiniClusterWrapper passed - using it for $useCaseForDebug")
        reusableMiniClusterWrapper
      }
      .getOrElse {
        logger.debug(s"reusableMiniClusterWrapper not passed - creating a new MiniCluster for $useCaseForDebug")
        createAdHocMiniClusterWrapper(scenario)
      }
    try {
      f(miniClusterWrapper)
    } finally {
      if (reusableMiniClusterWrapperOpt.isEmpty) {
        miniClusterWrapper.close()
      }
    }
  }

  private def createAdHocMiniClusterWrapper(process: CanonicalProcess) = {
    val scenarioParallelism = MetaDataExtractor
      .extractTypeSpecificDataOrDefault[StreamMetaData](process.metaData, StreamMetaData())
      .parallelism
      .getOrElse(1)
    ScenarioTestingMiniClusterWrapper.create(scenarioParallelism, new Configuration())
  }

}
