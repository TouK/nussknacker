package pl.touk.nussknacker.engine.graph

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.graph.expression.Expression

object evaluatedparam {

  object Parameter {
    //FIXME: after adding @JsonCodec
    //one can no longer write: Parameter.tupled, Parameter.apply is no longer recognized,
    // so lest we add this method we'd have to write (Parameter.apply _).tupled
    val tupled: ((String, Expression)) => Parameter = vals => Parameter(vals._1, vals._2, None)


    def apply(name: String, expression: Expression): Parameter = Parameter(name, expression, None)
  }


  @JsonCodec case class Parameter(name: String, expression: Expression, complexExpression: Option[ComplexExpression])

  @JsonCodec case class BranchParameters(branchId: String, parameters: List[Parameter])

  @JsonCodec case class ComplexExpression(parts: List[List[evaluatedparam.Parameter]])


}
