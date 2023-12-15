package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.Validated.{Valid, invalidNel}
import cats.data.ValidatedNel
import cats.implicits.toTraverseOps
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.context.{PartSubGraphCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.ValidationExpressionParameterValidator
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.expression.NullExpression
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{
  FixedExpressionValue,
  FragmentClazzRef,
  FragmentParameter
}
import pl.touk.nussknacker.engine.graph.node.{FixedValuesListFieldName, InitialValueFieldName}

class FragmentParameterValidator(
    expressionCompiler: ExpressionCompiler
) {

  def validateAndGetExpressionValidator(
      fragmentParameter: FragmentParameter,
      validationContext: ValidationContext // localVariables must include this and other FragmentParameters
  )(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, List[ValidationExpressionParameterValidator]] = {
    val unsupportedFixedValuesValidationResult = validateFixedValuesSupportedType(fragmentParameter)

    val fixedValuesListValidationResult = validateFixedValuesList(fragmentParameter)

    val fixedExpressionsValidationResult = validateFixedExpressionValues(
      fragmentParameter.initialValue,
      fragmentParameter.valueEditor.map(_.fixedValuesList).getOrElse(List.empty),
      validationContext,
      fragmentParameter.name
    )

    val validationExpressionResult =
      compileExpressionValidator(fragmentParameter, validationContext(fragmentParameter.name))

    List(
      unsupportedFixedValuesValidationResult,
      fixedValuesListValidationResult,
      fixedExpressionsValidationResult,
      validationExpressionResult
    ).sequence.map(_.flatten)
  }

  private def compileExpressionValidator(
      fragmentParameter: FragmentParameter,
      typ: TypingResult
  )(implicit nodeId: NodeId) =
    fragmentParameter.valueCompileTimeValidation.map { expr =>
      expressionCompiler
        .compile(
          expr.validationExpression,
          fieldName = Some(fragmentParameter.name),
          validationCtx = ValidationContext(
            Map(ValidationExpressionParameterValidator.variableName -> typ)
          ), // TODO in the future, we'd like to support more references, see ValidationExpressionParameterValidator
          expectedType = Typed[Boolean],
        )
        .leftMap(_.map {
          case e: ExpressionParserCompilationError =>
            InvalidValidationExpression(
              e.message,
              nodeId.id,
              fragmentParameter.name,
              e.originalExpr
            )
          case e => e
        })
        .andThen {
          _.expression match {
            case _: NullExpression =>
              invalidNel(
                InvalidValidationExpression(
                  "Validation expression cannot be blank",
                  nodeId.id,
                  fragmentParameter.name,
                  expr.validationExpression.expression
                )
              )
            case expression =>
              Valid(
                ValidationExpressionParameterValidator(
                  expression,
                  fragmentParameter.valueCompileTimeValidation.flatMap(_.validationFailedMessage)
                )
              )
          }
        }
    }.sequence

  private def validateFixedValuesSupportedType(fragmentParameter: FragmentParameter)(implicit nodeId: NodeId) =
    fragmentParameter.valueEditor match {
      case Some(_) =>
        if (List(FragmentClazzRef[java.lang.Boolean], FragmentClazzRef[String]).contains(fragmentParameter.typ)) {
          Valid(None)
        } else
          invalidNel(
            UnsupportedFixedValuesType(
              fragmentParameter.name,
              fragmentParameter.typ.refClazzName,
              nodeId.id
            )
          )
      case None => Valid(None)
    }

  private def validateFixedExpressionValues(
      initialValue: Option[FixedExpressionValue],
      fixedValuesList: List[FixedExpressionValue],
      validationContext: ValidationContext,
      paramName: String
  )(implicit nodeId: NodeId) = {
    def fixedExpressionsCompilationErrors(
        fixedExpressions: Iterable[FixedExpressionValue],
        subFieldName: Option[String],
    ) = {
      fixedExpressions
        .map { fixedExpressionValue =>
          expressionCompiler.compile(
            Expression.spel(fixedExpressionValue.expression),
            fieldName = Some(paramName),
            validationCtx = validationContext,
            expectedType = validationContext(paramName),
          )
        }
        .toList
        .sequence
        .leftMap(_.map {
          case e: ExpressionParserCompilationError =>
            ExpressionParserCompilationErrorInFragmentDefinition(
              e.message,
              nodeId.id,
              paramName,
              subFieldName,
              e.originalExpr
            )
          case e => e
        })
    }

    List(
      fixedExpressionsCompilationErrors(
        initialValue,
        Some(InitialValueFieldName)
      ),
      fixedExpressionsCompilationErrors(
        fixedValuesList,
        Some(FixedValuesListFieldName)
      )
    ).sequence.map(_ => None)
  }

  private def validateFixedValuesList(
      fragmentParameter: FragmentParameter,
  )(implicit nodeId: NodeId) =
    fragmentParameter.valueEditor match {
      case Some(valueEditor) if !valueEditor.allowOtherValue =>
        List(
          if (valueEditor.fixedValuesList.isEmpty)
            invalidNel(RequireValueFromEmptyFixedList(fragmentParameter.name, nodeId.id))
          else Valid(()),
          if (initialValueNotPresentInPossibleValues(fragmentParameter))
            invalidNel(InitialValueNotPresentInPossibleValues(fragmentParameter.name, nodeId.id))
          else Valid(())
        ).sequence.map(_ => None)
      case _ => Valid(None)
    }

  private def initialValueNotPresentInPossibleValues(
      fragmentParameter: FragmentParameter
  ) = (fragmentParameter.initialValue, fragmentParameter.valueEditor.map(_.fixedValuesList)) match {
    case (Some(value), Some(fixedValuesList)) if !fixedValuesList.contains(value) => true
    case _                                                                        => false
  }

}
