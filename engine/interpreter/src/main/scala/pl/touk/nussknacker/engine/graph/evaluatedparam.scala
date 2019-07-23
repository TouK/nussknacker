package pl.touk.nussknacker.engine.graph

import pl.touk.nussknacker.engine.graph.expression.Expression

object evaluatedparam {

  case class Parameter(name: String, expression: Expression)

  case class BranchParameters(branchId: String, parameters: List[Parameter])

}
