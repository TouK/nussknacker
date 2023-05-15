package pl.touk.nussknacker.restmodel.component

import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType

// TODO components-usages: discuss changing type to: List[ComponentUsages] where ComponentUsages is ComponentIdParts, List[NodeId].
case class ScenarioComponentsUsages(value: Map[ComponentIdParts, List[NodeId]])

object ScenarioComponentsUsages {
  val Empty: ScenarioComponentsUsages = ScenarioComponentsUsages(Map.empty)
}

case class ComponentIdParts(componentName: Option[String], componentType: ComponentType)
