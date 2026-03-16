package pl.touk.nussknacker.restmodel.component

import pl.touk.nussknacker.engine.api.component.ComponentId

final case class NodeIdWithNodeName(nodeId: NodeId, nodeName: NodeName)

final case class ScenarioComponentsUsages(value: Map[ComponentId, List[NodeIdWithNodeName]])

object ScenarioComponentsUsages {
  val Empty: ScenarioComponentsUsages = ScenarioComponentsUsages(Map.empty)
}
