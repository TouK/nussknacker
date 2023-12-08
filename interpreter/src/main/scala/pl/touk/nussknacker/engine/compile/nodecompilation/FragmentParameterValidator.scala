package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.Validated.{Valid, invalidNel}
import cats.data.ValidatedNel
import cats.implicits.toTraverseOps
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.{FixedExpressionValue => FixedValue}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition._
import pl.touk.nussknacker.engine.graph.node.{FixedValuesListFieldName, InitialValueFieldName}

class FragmentParameterValidator(
    expressionCompiler: ExpressionCompiler
) {

  def validate(
      fragmentParameter: FragmentParameter,
      fragmentInputId: String,
      usedFixedValuesPresets: Map[String, List[FixedValue]],
      validationContext: ValidationContext // localVariables must include this and other FragmentParameters
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit] = {
    val unsupportedFixedValuesValidationResult =
      validateFixedValuesSupportedType(fragmentParameter, fragmentInputId)

    val fixedValuesListValidationResult =
      validateFixedValuesListIfApplicable(fragmentParameter, fragmentInputId, usedFixedValuesPresets)

    val fixedExpressionsValidationResult = validateFixedExpressionValues(
      fragmentParameter.initialValue,
      fragmentParameter.valueEditor match {
        case Some(ValueInputWithFixedValuesProvided(fixedValuesList, _)) => fixedValuesList
        case _                                                           => List.empty
      },
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
          invalidNel(
            UnsupportedFixedValuesType(
              fragmentParameter.name,
              fragmentParameter.typ.refClazzName,
              Set(fragmentInputId)
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

  private def validateFixedValuesListIfApplicable(
      fragmentParameter: FragmentParameter,
      fragmentInputId: String,
      usedFixedValuesPresets: Map[String, List[FixedValue]]
  ): ValidatedNel[ProcessCompilationError, Unit] =
    fragmentParameter.valueEditor match {
      case Some(valueEditor) if !valueEditor.allowOtherValue =>
        getEffectiveFixedValuesList(valueEditor, usedFixedValuesPresets) match {
          case Some(fixedValuesList) => validateFixedValuesList(fixedValuesList, fragmentParameter, fragmentInputId)
          case None =>
            Valid(()) // PresetIdNotFoundInProvidedPresets are caught earlier in FragmentComponentDefinitionExtractor
        }

      case _ => Valid(())
    }

  private def getEffectiveFixedValuesList(
      valueEditor: ValueInputWithFixedValues,
      usedFixedValuesPresets: Map[String, List[FixedValue]]
  ) = valueEditor match {
    case ValueInputWithFixedValuesProvided(fixedValuesList, _) => Some(fixedValuesList)
    case ValueInputWithFixedValuesPreset(fixedValuesListPresetId, _) =>
      usedFixedValuesPresets.get(fixedValuesListPresetId).map(_.map(v => FixedExpressionValue(v.expression, v.label)))
  }

  private def validateFixedValuesList(
      fixedValuesList: List[FixedExpressionValue],
      fragmentParameter: FragmentParameter,
      fragmentInputId: String
  ) = {
    List(
      if (fixedValuesList.isEmpty)
        invalidNel(RequireValueFromEmptyFixedList(fragmentParameter.name, Set(fragmentInputId)))
      else Valid(()),
      if (initialValueNotPresentInPossibleValues(fragmentParameter, fixedValuesList))
        invalidNel(InitialValueNotPresentInPossibleValues(fragmentParameter.name, Set(fragmentInputId)))
      else Valid(())
    ).sequence.map(_ => ())
  }

  private def initialValueNotPresentInPossibleValues(
      fragmentParameter: FragmentParameter,
      effectiveFixedValuesList: List[FixedExpressionValue]
  ) = fragmentParameter.initialValue match {
    case Some(value) if !effectiveFixedValuesList.contains(value) => true
    case _                                                        => false
  }

}
