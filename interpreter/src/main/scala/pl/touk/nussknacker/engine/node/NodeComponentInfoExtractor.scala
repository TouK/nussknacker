package pl.touk.nussknacker.engine.node

import pl.touk.nussknacker.engine.api.component.NodeComponentInfo
import pl.touk.nussknacker.engine.compiledgraph.{node => compilednode}
import pl.touk.nussknacker.engine.graph.{node => scenarionode}

object NodeComponentInfoExtractor {

  def fromCompiledNode(node: compilednode.Node): NodeComponentInfo = {
    val componentId = ComponentIdExtractor.fromCompiledNode(node)
    NodeComponentInfo(node.id, componentId)
  }

  def fromScenarioNode(nodeData: scenarionode.NodeData): NodeComponentInfo = {
    val componentId = ComponentIdExtractor.fromScenarioNode(nodeData)
    NodeComponentInfo(nodeData.id, componentId)
  }

}
