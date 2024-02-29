package pl.touk.nussknacker.engine.definition.component.parameter.validator

import cats.data.Validated
import cats.data.Validated.{invalid, valid}
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.definition.Validator
import pl.touk.nussknacker.engine.api.{Context, NodeId}
import pl.touk.nussknacker.engine.definition.component.parameter.validator.ValidationExpressionParameterValidator.variableName
import pl.touk.nussknacker.engine.expression.parse.CompiledExpression
import pl.touk.nussknacker.engine.graph.expression.Expression

import scala.util.Try

case class ValidationExpressionParameterValidator(
    validationExpression: CompiledExpression,
    validationFailedMessage: Option[String]
) extends Validator {

  override def isValid(paramName: String, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] = {
    value match {
      case None       => valid(())
      case Some(null) => valid(())
      case Some(v)    => validateValue(paramName, v)
    }
  }

  private def validateValue(paramName: String, value: Any)(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] = {
    // TODO: paramName should be used here, but a lot of parameters have names that are not valid variables (e.g. "Topic name")
    val context = Context("validator", Map(variableName -> value), None)
    Try(validationExpression.evaluate[Boolean](context, Map())).fold(
      e =>
        invalid(
          CustomParameterValidationError(
            s"Evaluation of validation expression '${validationExpression.original}' of language ${validationExpression.language} failed: ${e.getMessage}",
            s"Please provide value that satisfies the validation expression '${validationExpression.original}'",
            paramName,
            nodeId.id
          )
        ),
      result => if (result) valid(()) else invalid(error(paramName, nodeId.id))
    )
  }

  private def error(paramName: String, nodeId: String): CustomParameterValidationError = CustomParameterValidationError(
    validationFailedMessage.getOrElse(
      s"This field has to satisfy the validation expression '${validationExpression.original}'"
    ),
    s"Please provide value that satisfies the validation expression '${validationExpression.original}'",
    paramName,
    nodeId
  )

}

object ValidationExpressionParameterValidator {

  val variableName = "value"

}
