package pl.touk.nussknacker.engine.flink.minicluster.scenariotesting

import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess

object ScenarioParallelismOverride {

  implicit class Ops(scenario: CanonicalProcess) {

    def overrideParallelism(parallelismOverride: Int): CanonicalProcess = {
      scenario.copy(metaData =
        scenario.metaData.copy(additionalFields =
          scenario.metaData.additionalFields.copy(properties =
            scenario.metaData.additionalFields.properties + (StreamMetaData.parallelismName -> parallelismOverride.toString)
          )
        )
      )
    }

  }

}
