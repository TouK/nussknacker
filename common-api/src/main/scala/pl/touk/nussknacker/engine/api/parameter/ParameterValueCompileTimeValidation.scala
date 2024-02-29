package pl.touk.nussknacker.engine.api.parameter

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.graph.expression.Expression
import sttp.tapir.Schema

@JsonCodec case class ParameterValueCompileTimeValidation(
    validationExpression: Expression,
    validationFailedMessage: Option[String]
)

object ParameterValueCompileTimeValidation {
  implicit val schema: Schema[ParameterValueCompileTimeValidation] = Schema.derived[ParameterValueCompileTimeValidation]
}
