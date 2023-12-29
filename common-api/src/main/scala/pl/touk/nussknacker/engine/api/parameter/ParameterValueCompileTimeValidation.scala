package pl.touk.nussknacker.engine.api.parameter

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.graph.expression.Expression

@JsonCodec case class ParameterValueCompileTimeValidation(
    validationExpression: Expression,
    validationFailedMessage: Option[String]
)
