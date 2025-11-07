package pl.touk.nussknacker.engine.definition.component.parameter.validator

import cats.data.Validated
import cats.data.Validated.{invalid, valid}
import pl.touk.nussknacker.engine.api.{Context, ContextId, JobData, NodeId}
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.definition.Validator
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.definition.component.parameter.validator.ValidationExpressionParameterValidator.variableName
import pl.touk.nussknacker.engine.expression.ExpressionEvaluator
import pl.touk.nussknacker.engine.expression.parse.CompiledExpression
import pl.touk.nussknacker.engine.graph.expression.Expression

import scala.util.Try

case class ValidationExpressionParameterValidator(
    validationExpression: CompiledExpression,
    validationFailedMessage: Option[String],
    expressionEvaluator: ExpressionEvaluator,
    jobData: JobData
) extends Validator {

  override def isValid(paramName: ParameterName, expression: Expression, value: Option[Any], label: Option[String])(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] =
    value match {
      // The expression may have dynamic value and may not be known when running compilation/validation.
      // In that case it is not possible to apply validation during validation/compilation.
      // Perhaps there should be a warning displayed in such situation, but at the moment it is not possible to return it.
      case None | Some(null) => valid(())
      case Some(v)           => validateValue(paramName, v)
    }

  private def validateValue(paramName: ParameterName, value: Any)(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] = {
    // TODO: paramName should be used here, but a lot of parameters have names that are not valid variables (e.g. "Topic name")
    val context = Context(ContextId.dummy, Map(variableName -> value), None, None)

    Try(
      expressionEvaluator.evaluate[java.lang.Boolean](validationExpression, "validationExpression", nodeId.id, context)(
        jobData
      )
    ).fold(
      e =>
        invalid(
          CustomParameterValidationError(
            message =
              s"Evaluation of validation expression '${validationExpression.original}' of language ${validationExpression.language} failed: ${e.getMessage}",
            description =
              s"Please provide value that satisfies the validation expression '${validationExpression.original}'",
            paramName = paramName,
            nodeId = nodeId.id
          )
        ),
      result => if (result.value) valid(()) else invalid(error(paramName, nodeId.id))
    )
  }

  private def error(paramName: ParameterName, nodeId: String): CustomParameterValidationError =
    CustomParameterValidationError(
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
