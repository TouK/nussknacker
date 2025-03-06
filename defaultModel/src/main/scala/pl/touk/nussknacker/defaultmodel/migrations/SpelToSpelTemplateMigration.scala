package pl.touk.nussknacker.defaultmodel.migrations

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.migration.NodeMigration

object SpelToSpelTemplateMigration extends NodeMigration {

  override def description: String = "Migrate empty text fields in Spel to SpelTemplate"

  override def migrateNode(metaData: MetaData): PartialFunction[node.NodeData, node.NodeData] = ???
}
