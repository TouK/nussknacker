package pl.touk.nussknacker.engine.process.scenariotesting

import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.util.MetaDataExtractor

// This class handles a legacy ad-hoc way to create minicluster.
// After we fully switch to single mini cluster approach, it should be removed
object AdHocMiniClusterFallbackHandler {

  def handleAdHocMniClusterFallback[R](
      reusableMiniClusterWrapperOpt: Option[ScenarioTestingMiniClusterWrapper],
      scenario: CanonicalProcess
  )(f: ScenarioTestingMiniClusterWrapper => R): R = {
    val miniClusterWrapper = reusableMiniClusterWrapperOpt.getOrElse {
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
