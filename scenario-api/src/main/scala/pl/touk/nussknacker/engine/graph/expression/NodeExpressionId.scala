package pl.touk.nussknacker.engine.graph.expression

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.parameter.ParameterName

case class NodeExpressionId(nodeId: NodeId, expressionId: String)

object NodeExpressionId {

  def apply(expressionId: String)(implicit nodeId: NodeId): NodeExpressionId =
    NodeExpressionId(nodeId, expressionId)

  val DefaultExpressionIdParamName: ParameterName = ParameterName("$expression")

  def branchParameterExpressionId(paramName: ParameterName, branch: String): String = {
    s"${paramName.value}-$branch"
  }

}
