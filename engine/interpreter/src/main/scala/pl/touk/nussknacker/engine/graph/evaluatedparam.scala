package pl.touk.nussknacker.engine.graph

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.graph.expression.Expression

object evaluatedparam {

  object Parameter {
    //FIXME: after adding @JsonCodec
    //one can no longer write: Parameter.tupled, Parameter.apply is no longer recognized,
    // so lest we add this method we'd have to write (Parameter.apply _).tupled
    val tupled: ((String, Expression)) => Parameter = (Parameter.apply _).tupled
  }

  @JsonCodec case class Parameter(name: String, expression: Expression)

  @JsonCodec case class BranchParameters(branchId: String, parameters: List[Parameter])

}
