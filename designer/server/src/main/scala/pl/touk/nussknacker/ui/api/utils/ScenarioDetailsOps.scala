package pl.touk.nussknacker.ui.api.utils

import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioWithDetails
import pl.touk.nussknacker.ui.process.label.ScenarioLabel

object ScenarioDetailsOps {

  implicit class ScenarioWithDetailsOps(val details: ScenarioWithDetails) extends AnyVal {

    def scenarioLabels: List[ScenarioLabel] = {
      details.labels.map(ScenarioLabel.apply)
    }

  }

}
