package pl.touk.nussknacker.ui.process.migrate

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.graph.node.{CustomNode, NodeData}
import pl.touk.nussknacker.engine.migration.NodeMigration

object GroupByMigration extends NodeMigration {

  override val description = "GroupByMigration"

  private val keyByParameterName = "keyBy"

  override def failOnNewValidationError: Boolean = false

  override def migrateNode(metadata: MetaData): PartialFunction[NodeData, NodeData] = {
    case node@CustomNode(_, _, nodeType, parameters, _)
      if parameters.exists(_.name == keyByParameterName) && nodeType.startsWith("aggregate-") =>
      node.copy(parameters = node.parameters.map(p => if (p.name == keyByParameterName) p.copy(name = "groupBy") else p))
  }
}
