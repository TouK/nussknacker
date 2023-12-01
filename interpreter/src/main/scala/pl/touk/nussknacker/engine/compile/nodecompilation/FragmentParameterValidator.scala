package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import cats.implicits.toTraverseOps
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
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

  def validate(
      fragmentParameter: FragmentParameter,
      fragmentInputId: String,
      validationContext: ValidationContext // localVariables must include this and other FragmentParameters
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit] = {
    val unsupportedFixedValuesValidationResult = validateFixedValuesSupportedType(fragmentParameter, fragmentInputId)

    val fixedValuesListValidationResult = validateFixedValuesList(fragmentParameter, fragmentInputId)

    val fixedExpressionsValidationResult = validateFixedExpressionValues(
      fragmentParameter.initialValue,
      fragmentParameter.valueEditor.map(_.fixedValuesList).getOrElse(List.empty),
      validationContext,
      fragmentParameter.name
    )

    List(
      unsupportedFixedValuesValidationResult,
      fixedValuesListValidationResult,
      fixedExpressionsValidationResult
    ).sequence.map(_ => ())
  }

  private def validateFixedValuesSupportedType(fragmentParameter: FragmentParameter, fragmentInputId: String) =
    fragmentParameter.valueEditor match {
      case Some(_) =>
        if (List(FragmentClazzRef[java.lang.Boolean], FragmentClazzRef[String]).contains(fragmentParameter.typ)) {
          Valid(())
        } else
          Invalid(
            NonEmptyList.of(
              UnsupportedFixedValuesType(
                fragmentParameter.name,
                fragmentParameter.typ.refClazzName,
                Set(fragmentInputId)
              )
            )
          )
      case None => Valid(())
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
    ).sequence.map(_ => ())
  }

  private def validateFixedValuesList(
      fragmentParameter: FragmentParameter,
      fragmentInputId: String
  ): ValidatedNel[ProcessCompilationError, Unit] =
    fragmentParameter.valueEditor match {
      case Some(valueEditor) if !valueEditor.allowOtherValue =>
        List(
          if (valueEditor.fixedValuesList.isEmpty)
            Invalid(
              NonEmptyList.of(RequireValueFromEmptyFixedList(fragmentParameter.name, Set(fragmentInputId)))
            )
          else Valid(()),
          if (initialValueNotPresentInPossibleValues(fragmentParameter))
            Invalid(
              NonEmptyList.of(InitialValueNotPresentInPossibleValues(fragmentParameter.name, Set(fragmentInputId)))
            )
          else Valid(())
        ).sequence.map(_ => ())
      case _ => Valid(())
    }

  private def initialValueNotPresentInPossibleValues(
      fragmentParameter: FragmentParameter
  ) = (fragmentParameter.initialValue, fragmentParameter.valueEditor.map(_.fixedValuesList)) match {
    case (Some(value), Some(fixedValuesList)) if !fixedValuesList.contains(value) => true
    case _                                                                        => false
  }

}
