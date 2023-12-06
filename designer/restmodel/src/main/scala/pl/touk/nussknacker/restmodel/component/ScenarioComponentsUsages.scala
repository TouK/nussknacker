package pl.touk.nussknacker.restmodel.component

import pl.touk.nussknacker.engine.api.component.ComponentInfo

final case class ScenarioComponentsUsages(value: Map[ComponentInfo, List[NodeId]])

object ScenarioComponentsUsages {
  val Empty: ScenarioComponentsUsages = ScenarioComponentsUsages(Map.empty)
}
