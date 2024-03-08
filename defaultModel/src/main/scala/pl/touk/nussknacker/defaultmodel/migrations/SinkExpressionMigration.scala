package pl.touk.nussknacker.defaultmodel.migrations

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.Sink
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.migration.NodeMigration

object SinkExpressionMigration extends NodeMigration {

  override def migrateNode(metaData: MetaData): PartialFunction[node.NodeData, node.NodeData] = {
    case sink @ Sink(_, ref @ SinkRef(typ, parameters), Some(legacyEndResult), _, _) if typ == "kafka-string" =>
      sink.copy(
        legacyEndResultExpression = None,
        ref = ref.copy(parameters = NodeParameter(ParameterName("value"), legacyEndResult) :: parameters)
      )
  }

  override def description: String = "Remove endResult from kafka-json"

}
