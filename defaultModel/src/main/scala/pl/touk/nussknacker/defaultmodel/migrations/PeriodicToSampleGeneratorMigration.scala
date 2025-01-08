package pl.touk.nussknacker.defaultmodel.migrations

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.Source
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.migration.NodeMigration

object PeriodicToSampleGeneratorMigration extends NodeMigration {

  override val description: String = "Change name of component: periodic -> sample-generator"

  override def migrateNode(metaData: MetaData): PartialFunction[node.NodeData, node.NodeData] = {
    case n @ Source(_, ref @ SourceRef("periodic", _), _) =>
      n.copy(ref = ref.copy(typ = "sample-generator"))
  }

}
