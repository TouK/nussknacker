package pl.touk.nussknacker.restmodel.component

import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType

final case class ScenarioComponentsUsages(value: Map[ComponentIdParts, List[NodeId]])

object ScenarioComponentsUsages {
  val Empty: ScenarioComponentsUsages = ScenarioComponentsUsages(Map.empty)
}

final case class ComponentIdParts(componentName: Option[String], componentType: ComponentType)
