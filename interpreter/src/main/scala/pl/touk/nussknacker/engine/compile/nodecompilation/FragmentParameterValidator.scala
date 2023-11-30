package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
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
    val unsupportedFixedValuesTypeResponse = validateFixedValuesSupportedType(fragmentParameter, fragmentInputId)

    val fixedExpressionsResponses = validateFixedExpressionValues(
      fragmentParameter.initialValue,
      fragmentParameter.valueEditor.map(_.fixedValuesList).getOrElse(List.empty),
      validationContext,
      fragmentParameter.name
    )

    val fixedValuesListResponses = validateFixedValuesList(fragmentParameter, fragmentInputId)

    toValidatedNel(unsupportedFixedValuesTypeResponse ++ fixedValuesListResponses ++ fixedExpressionsResponses)
  }

  private def toValidatedNel(errors: Iterable[ProcessCompilationError]) = errors.toList match {
    case head :: tail => Invalid(NonEmptyList(head, tail))
    case Nil          => Valid(())
  }

  private def validateFixedValuesSupportedType(fragmentParameter: FragmentParameter, fragmentInputId: String) =
    fragmentParameter.valueEditor match {
      case Some(_) =>
        if (!List(FragmentClazzRef[java.lang.Boolean], FragmentClazzRef[String]).contains(fragmentParameter.typ))
          List(
            UnsupportedFixedValuesType(
              fragmentParameter.name,
              fragmentParameter.typ.refClazzName,
              Set(fragmentInputId)
            )
          )
        else
          List.empty
      case None => List.empty
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
    ) = fixedExpressions
      .flatMap { fixedExpressionValue =>
        expressionCompiler.compile(
          Expression.spel(fixedExpressionValue.expression),
          fieldName = Some(paramName),
          validationCtx = validationContext,
          expectedType = validationContext(paramName),
        ) match {
          case Valid(_)   => List.empty
          case Invalid(e) => e.toList
        }
      }
      .map {
        case e: ExpressionParserCompilationError =>
          ExpressionParserCompilationErrorInFragmentDefinition(
            e.message,
            nodeId.id,
            paramName,
            subFieldName,
            e.originalExpr
          )
        case e => e
      }

    fixedExpressionsCompilationErrors(
      initialValue,
      Some(InitialValueFieldName)
    ) ++ fixedExpressionsCompilationErrors(
      fixedValuesList,
      Some(FixedValuesListFieldName)
    )
  }

  private def validateFixedValuesList(fragmentParameter: FragmentParameter, fragmentInputId: String) =
    fragmentParameter.valueEditor match {
      case Some(valueEditor) if !valueEditor.allowOtherValue =>
        (if (valueEditor.fixedValuesList.isEmpty)
           List(RequireValueFromEmptyFixedList(fragmentParameter.name, Set(fragmentInputId)))
         else List.empty) ++
          (if (initialValueNotPresentInPossibleValues(fragmentParameter))
             List(InitialValueNotPresentInPossibleValues(fragmentParameter.name, Set(fragmentInputId)))
           else List.empty)
      case _ => List.empty
    }

  private def initialValueNotPresentInPossibleValues(
      fragmentParameter: FragmentParameter
  ) = (fragmentParameter.initialValue, fragmentParameter.valueEditor.map(_.fixedValuesList)) match {
    case (Some(value), Some(fixedValuesList)) if !fixedValuesList.contains(value) => true
    case _                                                                        => false
  }

}
