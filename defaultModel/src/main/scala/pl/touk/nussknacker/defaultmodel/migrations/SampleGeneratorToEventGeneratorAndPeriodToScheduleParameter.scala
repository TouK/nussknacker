package pl.touk.nussknacker.defaultmodel.migrations

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.Source
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.engine.migration.NodeMigration

object SampleGeneratorToEventGeneratorAndPeriodToScheduleParameter extends NodeMigration {

  override val description: String = "Change name of component: sample-generator -> event-generator " +
    "and its parameter: period -> schedule"

  override def migrateNode(metaData: MetaData): PartialFunction[node.NodeData, node.NodeData] = {
    case source @ Source(_, ref @ SourceRef("sample-generator", _), _) =>
      source.copy(ref =
        ref.copy(
          typ = "event-generator",
          parameters = ref.parameters.map { param =>
            param.name.value match {
              case "period" => param.copy(name = ParameterName("schedule"))
              case _        => param
            }
          }
        )
      )
  }

}
