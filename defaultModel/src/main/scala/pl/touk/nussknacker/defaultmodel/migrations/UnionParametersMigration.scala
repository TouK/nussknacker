package pl.touk.nussknacker.defaultmodel.migrations

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.graph.node.{CustomNode, NodeData}
import pl.touk.nussknacker.engine.migration.NodeMigration

/**
  * If you want to apply this migration for different node type create new case class instance with appropriate name
  *
  * @param migratedNodeType - type of node to be migrated eg. union, singleTypeUnion
  */
case class UnionParametersMigration(migratedNodeType: String = "union") extends NodeMigration {

  override val description = "UnionParametersMigration"

  private val oldValueParameterName            = ParameterName("value")
  private val newOutputExpressionParameterName = ParameterName("Output expression")

  override def migrateNode(metadata: MetaData): PartialFunction[NodeData, NodeData] = {
    case node @ CustomNode(_, _, nodeType, parameters, _)
        if parameters.exists(_.name == oldValueParameterName) && nodeType == migratedNodeType =>
      node.copy(parameters =
        node.parameters.map(p =>
          if (p.name == oldValueParameterName) p.copy(name = newOutputExpressionParameterName) else p
        )
      )
  }

}
