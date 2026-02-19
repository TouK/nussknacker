package pl.touk.nussknacker.defaultmodel.migrations

import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.graph.evaluatedparam.Parameter
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node.CustomNode
import pl.touk.nussknacker.engine.migration.NodeMigration

object PreviousValueParameterNamesMigration extends NodeMigration {

  override val description: String = "Rename Previous Value component parameters"

  override def migrateNode(metaData: MetaData): PartialFunction[node.NodeData, node.NodeData] = {
    case customNode @ CustomNode(_, _, _, "previousValue", parameters, _) =>
      customNode.copy(parameters = renameParameters(parameters))
  }

  private def renameParameters(parameters: List[Parameter]) = {
    parameters.map {
      case param @ Parameter(ParameterName("groupBy"), _) =>
        param.copy(name = ParameterName("Key"))
      case param @ Parameter(ParameterName("value"), _) =>
        param.copy(name = ParameterName("Value"))
      case param => param
    }
  }

}
