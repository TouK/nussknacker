package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.Validated.{Valid, invalid, invalidNel, valid}
import cats.data.ValidatedNel
import cats.implicits.toTraverseOps
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.context.{PartSubGraphCompilationError, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ValueInputWithFixedValues
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
      paramName: String,
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

  def validateFixedValuesAndGetExpressionValidator(
      fragmentParameter: FragmentParameter,
      validationContext: ValidationContext, // localVariables must include this and other FragmentParameters
      expressionCompiler: ExpressionCompiler
  )(
      implicit nodeId: NodeId
  ): ValidatedNel[PartSubGraphCompilationError, Option[ValidationExpressionParameterValidator]] = {

    val fixedExpressionValuesValidationResult = validateFixedExpressionValues(
      fragmentParameter.initialValue,
      fragmentParameter.valueEditor.map(_.fixedValuesList).getOrElse(List.empty),
      validationContext,
      expressionCompiler,
      fragmentParameter.name,
    )

    val validationExpressionResult = fragmentParameter.valueCompileTimeValidation
      .map(validation =>
        expressionCompiler
          .compileValidationExpressionParameterValidator(
            ValidationExpressionParameterValidatorToCompile(validation),
            fragmentParameter.name,
            validationContext(fragmentParameter.name)
          )
          .map(Some(_))
      )
      .getOrElse(Valid(None))

    List(
      fixedExpressionValuesValidationResult.map(_ => None),
      validationExpressionResult
    ).sequence.map(_.flatten.headOption)
  }

  private def validateFixedValuesSupportedType(
      refClazz: FragmentClazzRef,
      paramName: String,
      nodeIds: Set[String]
  ): ValidatedNel[PartSubGraphCompilationError, Unit] =
    if (List(FragmentClazzRef[java.lang.Boolean], FragmentClazzRef[String]).contains(refClazz)) {
      Valid(())
    } else
      invalidNel(
        UnsupportedFixedValuesType(
          paramName,
          refClazz.refClazzName,
          nodeIds
        )
      )

  private def validateFixedExpressionValues(
      initialValue: Option[FixedExpressionValue],
      fixedValuesList: List[FixedExpressionValue],
      validationContext: ValidationContext,
      expressionCompiler: ExpressionCompiler,
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
          paramName,
          Some(qualifiedParamFieldName(paramName, Some(ParameterNameFieldName)))
        ).map(_ => ())
        acc.combine(duplicationError).combine(validIdentifierError)
      }
  }

}
