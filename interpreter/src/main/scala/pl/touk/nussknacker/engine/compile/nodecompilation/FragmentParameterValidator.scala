package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.Validated.{Valid, invalid, invalidNel, valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits.toTraverseOps
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError._
import pl.touk.nussknacker.engine.api.context.{PartSubGraphCompilationError, ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.dict.DictDefinition
import pl.touk.nussknacker.engine.api.parameter.{
  ParameterValueInput,
  ValueInputWithDictEditor,
  ValueInputWithFixedValuesProvided
}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.api.validation.Validations.validateVariableName
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.FragmentInputDefinition.{FragmentClazzRef, FragmentParameter}
import pl.touk.nussknacker.engine.graph.node.{
  DictIdFieldName,
  FixedValuesListFieldName,
  InitialValueFieldName,
  ParameterNameFieldName,
  qualifiedParamFieldName
}
import pl.touk.nussknacker.engine.language.dictWithLabel.DictKeyWithLabelExpressionParser

object FragmentParameterValidator {

  // This method doesn't fully validate valueEditor (see ValueEditorValidator.validateAndGetEditor comments)
  def validateAgainstClazzRefAndGetEditor(
      valueEditor: ParameterValueInput,
      initialValue: Option[FixedExpressionValue],
      refClazz: FragmentClazzRef,
      paramName: ParameterName,
      nodeIds: Set[String]
  ): ValidatedNel[PartSubGraphCompilationError, ParameterEditor] = {
    validateValueEditorSupportedType(valueEditor, refClazz, paramName, nodeIds)
      .andThen(_ =>
        ValueEditorValidator.validateAndGetEditor(
          valueEditor,
          initialValue,
          paramName,
          nodeIds
        )
      )
  }

  private def validateValueEditorSupportedType(
      valueEditor: ParameterValueInput,
      refClazz: FragmentClazzRef,
      paramName: ParameterName,
      nodeIds: Set[String]
  ): ValidatedNel[PartSubGraphCompilationError, Unit] =
    valueEditor match {
      case ValueInputWithFixedValuesProvided(_, _) =>
        if (List(FragmentClazzRef[java.lang.Boolean], FragmentClazzRef[String]).contains(refClazz))
          Valid(())
        else
          invalidNel(UnsupportedFixedValuesType(paramName, refClazz.refClazzName, nodeIds))
      case ValueInputWithDictEditor(_, _) =>
        if (List(FragmentClazzRef[java.lang.Boolean], FragmentClazzRef[String], FragmentClazzRef[java.lang.Long])
            .contains(refClazz)) {
          Valid(())
        } else {
          invalidNel(UnsupportedDictParameterEditorType(paramName, refClazz.refClazzName, nodeIds))
        }
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
          val expr = fragmentParameter.valueEditor match {
            case Some(ValueInputWithDictEditor(_, _)) =>
              DictKeyWithLabelExpressionParser
                .parseDictKeyWithLabelExpression(fixedExpressionValue.expression)
                .leftMap(errs => errs.map(_.toProcessCompilationError(nodeId.id, fragmentParameter.name)))
                .andThen(e => valid(Expression.dictKeyWithLabel(e.key, e.label)))
            case _ => valid(Expression.spel(fixedExpressionValue.expression))
          }

          expr.andThen(e =>
            expressionCompiler.compile(
              e,
              paramName = Some(fragmentParameter.name),
              validationCtx = validationContext,
              expectedType = validationContext(fragmentParameter.name.value),
            )
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

    val fixedValuesList = fragmentParameter.valueEditor match {
      case Some(ValueInputWithFixedValuesProvided(fixedValuesList, _)) => fixedValuesList
      case _                                                           => List.empty
    }

    List(
      fixedExpressionsCompilationErrors(
        fragmentParameter.initialValue,
        Some(InitialValueFieldName)
      ),
      fixedExpressionsCompilationErrors(
        fixedValuesList,
        Some(FixedValuesListFieldName)
      )
    ).sequence.map(_ => ())
  }

  def validateValueInputWithDictEditor(
      fragmentParameter: FragmentParameter,
      dictionaries: Map[String, DictDefinition],
      classLoader: ClassLoader
  )(implicit nodeId: NodeId): Validated[NonEmptyList[PartSubGraphCompilationError], Unit] =
    fragmentParameter.valueEditor match {
      case Some(ValueInputWithDictEditor(dictId, _)) =>
        validateNonEmptyDictId(dictId, fragmentParameter.name).andThen(_ =>
          dictionaries.get(dictId) match {
            case Some(dictDefinition) =>
              val fragmentParameterTypingResult = fragmentParameter.typ
                .toRuntimeClass(classLoader)
                .map(Typed(_))
                .getOrElse(Unknown)

              val dictValueType = dictDefinition.valueType(dictId)

              if (dictValueType.canBeSubclassOf(fragmentParameterTypingResult)) {
                Valid(())
              } else {
                invalidNel(
                  DictIsOfInvalidType(
                    dictId,
                    dictValueType,
                    fragmentParameterTypingResult,
                    nodeId.id,
                    qualifiedParamFieldName(fragmentParameter.name, Some(DictIdFieldName))
                  )
                )
              }
            case None =>
              invalidNel(
                DictNotDeclared(
                  dictId,
                  nodeId.id,
                  qualifiedParamFieldName(fragmentParameter.name, Some(DictIdFieldName))
                )
              )
          }
        )

      case _ => Valid(())
    }

  private def validateNonEmptyDictId(dictId: String, parameterName: ParameterName)(implicit nodeId: NodeId) =
    if (dictId.isBlank)
      invalidNel(
        EmptyMandatoryParameterConfigurationField(
          nodeId.id,
          qualifiedParamFieldName(parameterName, Some(DictIdFieldName))
        )
      )
    else
      valid(())

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
