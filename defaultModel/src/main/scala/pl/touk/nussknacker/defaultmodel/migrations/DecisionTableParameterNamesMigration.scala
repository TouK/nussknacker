package pl.touk.nussknacker.defaultmodel.migrations

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.Enricher
import pl.touk.nussknacker.engine.graph.service.ServiceRef
import pl.touk.nussknacker.engine.migration.NodeMigration

object DecisionTableParameterNamesMigration extends NodeMigration {

  override val description: String = "Change Decision Table component parameter names"

  override def migrateNode(metaData: MetaData): PartialFunction[node.NodeData, node.NodeData] = {
    case enricher @ Enricher(_, service @ ServiceRef(_, params), _, _) =>
      enricher.copy(service = service.copy(parameters = renameParams(params)))
  }

  private def renameParams(params: List[Parameter]) = {
    params.map { param =>
      param.name.value match {
        case "Basic Decision Table" => param.copy(name = ParameterName("Decision Table"))
        case "Expression"           => param.copy(name = ParameterName("Filtering expression"))
        case _                      => param
      }
    }
  }

}
