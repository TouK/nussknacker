package pl.touk.nussknacker.defaultmodel.migrations

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.graph.node.{CustomNode, NodeData}
import pl.touk.nussknacker.engine.migration.NodeMigration

case class ProcessSplitterMigration(migratedNodeType: String = "split") extends NodeMigration {

  override val description = "ProcessSplitterMigration"

  private val newNodeType = "for-each"

  private val oldElementsParameterName = ParameterName("parts")
  private val newElementsParameterName = ParameterName("Elements")

  override def migrateNode(metadata: MetaData): PartialFunction[NodeData, NodeData] = {
    case node @ CustomNode(_, _, nodeType, _, _) if nodeType == migratedNodeType =>
      node.copy(
        nodeType = newNodeType,
        parameters = node.parameters.map(p =>
          if (p.name == oldElementsParameterName) p.copy(name = newElementsParameterName) else p
        )
      )
  }

}
