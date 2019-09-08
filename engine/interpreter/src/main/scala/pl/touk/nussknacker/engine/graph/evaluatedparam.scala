package pl.touk.nussknacker.engine.graph

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.graph.expression.Expression

object evaluatedparam {

  object Parameter {
    //FIXME:
    def tupled(tpl: (String, Expression)) = Parameter(tpl._1, tpl._2)
  }

  @JsonCodec case class Parameter(name: String, expression: Expression)

  @JsonCodec case class BranchParameters(branchId: String, parameters: List[Parameter])

}
