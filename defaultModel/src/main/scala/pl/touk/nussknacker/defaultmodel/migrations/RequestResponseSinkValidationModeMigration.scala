package pl.touk.nussknacker.defaultmodel.migrations

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.graph.evaluatedparam.{Parameter => NodeParameter}
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.Sink
import pl.touk.nussknacker.engine.graph.sink.SinkRef
import pl.touk.nussknacker.engine.migration.NodeMigration

object RequestResponseSinkValidationModeMigration extends NodeMigration {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  private val validationModeParam = NodeParameter(ParameterName("Value validation mode"), "'lax'".spel)

  override def migrateNode(metaData: MetaData): PartialFunction[node.NodeData, node.NodeData] = {
    case sink @ Sink(_, ref @ SinkRef(typ, parameters), _, _, _) if typ == "response" =>
      sink.copy(ref = ref.copy(parameters = parameters ++ List(validationModeParam)))
  }

  override def description: String =
    "Add value validation mode param to rr response sink: https://github.com/TouK/nussknacker/pull/3727"

}
