package pl.touk.nussknacker.ui.process.repository

import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.restmodel.component.NodeId

case class ScenarioComponentsUsages(value: Map[ComponentIdParts, List[NodeId]])

object ScenarioComponentsUsages {
  val Empty: ScenarioComponentsUsages = ScenarioComponentsUsages(Map.empty)
}

case class ComponentIdParts(componentName: Option[String], componentType: ComponentType)
