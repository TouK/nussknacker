package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.Validated.{Valid, invalid, invalidNel, valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits.toTraverseOps
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.context.{PartSubGraphCompilationError, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.{ParameterName, ValueInputWithFixedValues}
import pl.touk.nussknacker.engine.api.validation.Validations.validateVariableName
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.node.{
  FixedValuesListFieldName,
  InitialValueFieldName,
  ParameterNameFieldName,
  qualifiedParamFieldName
}

object FragmentParameterValidator {

  def validateAgainstClazzRefAndGetEditor( // this method doesn't validate the compilation validity of FixedExpressionValues (it requires validationContext and expressionCompiler, see FragmentParameterValidator.validateFixedExpressionValues)
      valueEditor: ValueInputWithFixedValues,
      initialValue: Option[FixedExpressionValue],
      refClazz: FragmentClazzRef,
      paramName: ParameterName,
      nodeIds: Set[String]
  ): ValidatedNel[PartSubGraphCompilationError, ParameterEditor] = {
    validateFixedValuesSupportedType(refClazz, paramName, nodeIds)
      .andThen(_ =>
        ValueEditorValidator.validateAndGetEditor(
          valueEditor,
          initialValue,
          paramName,
          nodeIds
        )
      )
  }

  private def validateFixedValuesSupportedType(
      refClazz: FragmentClazzRef,
      paramName: ParameterName,
      nodeIds: Set[String]
  ): ValidatedNel[PartSubGraphCompilationError, Unit] =
    if (List(FragmentClazzRef[java.lang.Boolean], FragmentClazzRef[String]).contains(refClazz)) {
      Valid(())
    } else {
      invalidNel(UnsupportedFixedValuesType(paramName, refClazz.refClazzName, nodeIds))
    }

  def validateFixedExpressionValues(
      fragmentParameter: FragmentParameter,
      validationContext: ValidationContext, // localVariables must include this and other FragmentParameters
      expressionCompiler: ExpressionCompiler
  )(implicit nodeId: NodeId): Validated[NonEmptyList[PartSubGraphCompilationError], Unit] = {
    def fixedExpressionsCompilationErrors(
        fixedExpressions: Iterable[FixedExpressionValue],
        subFieldName: Option[String],
    ) = {
      fixedExpressions
        .map { fixedExpressionValue =>
          expressionCompiler.compile(
            Expression.spel(fixedExpressionValue.expression),
            paramName = Some(fragmentParameter.name),
            validationCtx = validationContext,
            expectedType = validationContext(fragmentParameter.name.value),
          )
        }
        .toList
        .sequence
        .leftMap(_.map {
          case e: ExpressionParserCompilationError =>
            ExpressionParserCompilationErrorInFragmentDefinition(
              message = e.message,
              nodeId = nodeId.id,
              paramName = fragmentParameter.name,
              subFieldName = subFieldName,
              originalExpr = e.originalExpr
            )
          case e => e
        })
    }

    List(
      fixedExpressionsCompilationErrors(
        fragmentParameter.initialValue,
        Some(InitialValueFieldName)
      ),
      fixedExpressionsCompilationErrors(
        fragmentParameter.valueEditor.map(_.fixedValuesList).getOrElse(List.empty),
        Some(FixedValuesListFieldName)
      )
    ).sequence.map(_ => ())
  }

  def validateParameterNames(
      parameters: List[Parameter]
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit] = {
    parameters
      .map(_.name)
      .groupBy(identity)
      .foldLeft(valid(()): ValidatedNel[ProcessCompilationError, Unit]) { case (acc, (paramName, group)) =>
        val duplicationError = if (group.size > 1) {
          invalid(DuplicateFragmentInputParameter(paramName, nodeId.toString)).toValidatedNel
        } else valid(())
        val validIdentifierError = validateVariableName(
          paramName.value,
          Some(qualifiedParamFieldName(paramName, Some(ParameterNameFieldName)))
        ).map(_ => ())
        acc.combine(duplicationError).combine(validIdentifierError)
      }
  }

}
