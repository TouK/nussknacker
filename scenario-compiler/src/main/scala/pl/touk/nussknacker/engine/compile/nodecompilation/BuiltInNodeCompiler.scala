package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.Validated.{invalid, valid, Invalid, Valid}
import cats.data.ValidatedNel
import cats.implicits.{catsSyntaxTuple2Semigroupal, toFoldableOps, toTraverseOps}
import cats.instances.list._
import pl.touk.nussknacker.engine.api.{Context, NodeId}
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomParameterValidationError
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.validation.Validations.validateVariableName
import pl.touk.nussknacker.engine.compile._
import pl.touk.nussknacker.engine.compile.nodecompilation.BaseComponentValidationHelper._
import pl.touk.nussknacker.engine.compile.nodecompilation.BuiltInNodeCompiler._
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler.NodeCompilationResult
import pl.touk.nussknacker.engine.compiledgraph
import pl.touk.nussknacker.engine.expression.parse.{CompiledExpression, TypedExpression}
import pl.touk.nussknacker.engine.graph.expression._
import pl.touk.nussknacker.engine.graph.expression.NodeExpressionId.DefaultExpressionIdParamName
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.graph.node.variablesToUnsetKeyFieldName
import pl.touk.nussknacker.engine.graph.variable.{Field => GraphField}

class BuiltInNodeCompiler(expressionCompiler: ExpressionCompiler) {

  def compileVariable(variable: Variable, inputContext: SingleInputNodeInputValidationContext)(
      implicit nodeId: NodeId
  ): NodeCompilationResult[CompiledExpression] = {
    val (validTypedExpression, nodeCompilation) =
      compileExpression(
        variable.value,
        inputContext.validationContext,
        expectedType = Unknown,
        paramName = DefaultExpressionIdParamName,
        outputVar = Some(OutputVar.variable(variable.varName))
      )

    val additionalValidationResult =
      validateVariableValue(validTypedExpression, DefaultExpressionIdParamName, inputContext)

    combineErrors(nodeCompilation, additionalValidationResult)
  }

  def compileVariableUnset(variable: Variable, inputContext: SingleInputNodeInputValidationContext)(
      implicit nodeId: NodeId
  ): NodeCompilationResult[List[String]] = {
    val sanitizedUnsetVariablesWithIndexes = variable.variablesToUnset.zipWithIndex.map { case (field, index) =>
      (field.name.trim, index)
    }

    val atLeastOneVariableValidation: ValidatedNel[PartSubGraphCompilationError, Unit] =
      if (sanitizedUnsetVariablesWithIndexes.nonEmpty) {
        valid(())
      } else {
        invalid[PartSubGraphCompilationError, Unit](
          CustomParameterValidationError(
            "At least one variable has to be selected",
            "Please add at least one variable to unset",
            ParameterName("variablesToUnset"),
            nodeId
          )
        ).toValidatedNel
      }

    val variableNamesValidation = sanitizedUnsetVariablesWithIndexes.map { case (variableName, index) =>
      val fieldParameterName = ParameterName(variablesToUnsetKeyFieldName(index))
      val blankValidation: ValidatedNel[PartSubGraphCompilationError, Unit] =
        if (variableName.nonEmpty) {
          valid(())
        } else {
          invalid[PartSubGraphCompilationError, Unit](
            CustomParameterValidationError(
              "This field value is required and can not be blank",
              "Please fill field value for this parameter",
              fieldParameterName,
              nodeId
            )
          ).toValidatedNel
        }
      val nameValidation = (blankValidation, validateVariableName(variableName, Some(fieldParameterName))).tupled
      val existsValidation: ValidatedNel[PartSubGraphCompilationError, Unit] =
        if (inputContext.validationContext.localVariables.contains(variableName)) {
          valid(())
        } else {
          invalid[PartSubGraphCompilationError, Unit](
            CustomParameterValidationError(
              "Can only unset variables available in the context",
              "Variable not found in the current context",
              fieldParameterName,
              nodeId
            )
          ).toValidatedNel
        }
      nameValidation.andThen(_ => existsValidation).map(_ => variableName)
    }.sequence

    val uniqueNamesValidation: ValidatedNel[PartSubGraphCompilationError, Unit] = {
      val duplicatedIndexes = sanitizedUnsetVariablesWithIndexes
        .groupBy(_._1)
        .values
        .filter(_.size > 1)
        .flatMap(_.map(_._2))
        .toList

      if (duplicatedIndexes.isEmpty) {
        valid(())
      } else {
        Invalid(
          cats.data.NonEmptyList.fromListUnsafe(
            duplicatedIndexes.map[PartSubGraphCompilationError](index =>
              CustomParameterValidationError(
                "The variable can be unset only once",
                "Variable selected more than once",
                ParameterName(variablesToUnsetKeyFieldName(index)),
                nodeId
              )
            )
          )
        )
      }
    }

    val validVariablesToUnset = ((atLeastOneVariableValidation, variableNamesValidation).tupled, uniqueNamesValidation)
      .mapN { case ((_, variableNames), _) => variableNames }

    // Keep UNSET validation/runtime behavior aligned: remove only local context variables and avoid duplicating
    // the same errors in both compiledObject and validationContext.
    val resultValidationContext = validVariablesToUnset match {
      case Valid(variableNames) => Valid(inputContext.validationContext.withoutVariables(variableNames))
      case Invalid(_)           => Valid(inputContext.validationContext)
    }

    NodeCompilationResult(
      expressionTypingInfo = Map.empty,
      parameters = None,
      validationContext = resultValidationContext,
      compiledObject = validVariablesToUnset
    )
  }

  def compileFilter(filter: Filter, inputContext: SingleInputNodeInputValidationContext)(
      implicit nodeId: NodeId
  ): NodeCompilationResult[CompiledExpression] = {
    val (validTypedExpression, nodeCompilation) =
      compileExpression(
        filter.expression,
        inputContext.validationContext,
        expectedType = Typed[Boolean],
        paramName = DefaultExpressionIdParamName,
        outputVar = None
      )

    val additionalValidationResult = validateBoolean(validTypedExpression, DefaultExpressionIdParamName, inputContext)

    combineErrors(nodeCompilation, additionalValidationResult)
  }

  def compileSwitch(
      expressionRaw: Option[(String, Expression)],
      choices: List[(String, Expression)],
      inputContext: SingleInputNodeInputValidationContext
  )(
      implicit nodeId: NodeId
  ): NodeCompilationResult[(Option[CompiledExpression], List[CompiledExpression])] = {

    // the frontend uses empty string to delete deprecated expression.
    val expression = expressionRaw.filterNot(_._1.isEmpty)

    val expressionCompilation = expression.map { case (output, expression) =>
      compileExpression(
        expr = expression,
        ctx = inputContext.validationContext,
        expectedType = Unknown,
        paramName = NodeExpressionId.DefaultExpressionIdParamName,
        outputVar = Some(OutputVar.switch(output))
      )._2
    }
    val objExpression = expressionCompilation.map(_.compiledObject).sequence

    val caseCtx = expressionCompilation.flatMap(_.validationContext.toOption).getOrElse(inputContext.validationContext)

    val (additionalValidations, caseExpressions) = choices.map { case (outEdge, caseExpr) =>
      val outEdgeParamName = ParameterName(outEdge)
      val (validTypedExpression, nodeCompilation) =
        compileExpression(caseExpr, caseCtx, Typed[Boolean], outEdgeParamName, None)
      val validation     = validateBoolean(validTypedExpression, outEdgeParamName, inputContext)
      val caseExpression = nodeCompilation
      (validation, caseExpression)
    }.unzip

    val expressionTypingInfos = caseExpressions
      .map(_.expressionTypingInfo)
      .foldLeft(expressionCompilation.map(_.expressionTypingInfo).getOrElse(Map.empty)) {
        _ ++ _
      }

    val objCases = caseExpressions.map(_.compiledObject).sequence

    val compilationResult = NodeCompilationResult(
      expressionTypingInfos,
      None,
      expressionCompilation.map(_.validationContext).getOrElse(Valid(inputContext.validationContext)),
      objExpression.product(objCases),
      expressionCompilation.flatMap(_.expressionType)
    )

    combineErrors(compilationResult, additionalValidations.combineAll)
  }

  def compileFields(
      fields: List[GraphField],
      inputContext: SingleInputNodeInputValidationContext,
      outputVar: Option[OutputVar]
  )(implicit nodeId: NodeId): NodeCompilationResult[List[compiledgraph.variable.Field]] = {

    val (compiledRecord, indexedFields) = {
      val compiledFields = fields.zipWithIndex.map { case (field, index) =>
        val compiledField = expressionCompiler
          .compile(
            field.expression,
            Some(ParameterName(node.recordValueFieldName(index))),
            inputContext.validationContext,
            Unknown
          )
          .map(result =>
            CompiledIndexedRecordField(compiledgraph.variable.Field(field.name, result.expression), index, result)
          )
        val indexedKeys = IndexedRecordKey(field.name, index)
        (compiledField, indexedKeys)
      }
      (compiledFields.map(_._1).sequence, compiledFields.map(_._2))
    }

    val typedObject = compiledRecord match {
      case Valid(fields) =>
        Typed.record(fields.map(f => (f.field.name, typedExprToTypingResult(Some(f.typedExpression)))))
      case Invalid(_) => Unknown
    }

    val fieldsTypingInfo: Map[String, ExpressionTypingInfo] = compiledRecord match {
      case Valid(fields) =>
        fields.flatMap(f => typedExprToTypingInfo(Some(f.typedExpression), ParameterName(f.field.name))).toMap
      case Invalid(_) => Map.empty
    }

    val compiledFields = compiledRecord.map(_.map(_.field))

    val compilationResult = NodeCompilationResult(
      expressionTypingInfo = fieldsTypingInfo,
      parameters = None,
      validationContext = outputVar
        .map(inputContext.validationContext.withVariable(_, typedObject))
        .getOrElse(Valid(inputContext.validationContext)),
      compiledObject = compiledFields,
      expressionType = Some(typedObject)
    )

    val additionalValidationResult = RecordValidator.validate(compiledRecord, indexedFields, inputContext)

    combineErrors(compilationResult, additionalValidationResult)
  }

  private def compileExpression(
      expr: Expression,
      ctx: ValidationContext,
      expectedType: TypingResult,
      paramName: ParameterName,
      outputVar: Option[OutputVar]
  )(
      implicit nodeId: NodeId
  ): (ValidatedNel[ProcessCompilationError, TypedExpression], NodeCompilationResult[CompiledExpression]) = {
    val validTypedExpression = expressionCompiler
      .compile(expr, Some(paramName), ctx, expectedType)

    val typingResult = typedExprToTypingResult(validTypedExpression.toOption)

    val nodeCompilation: NodeCompilationResult[CompiledExpression] = NodeCompilationResult(
      expressionTypingInfo = typedExprToTypingInfo(validTypedExpression.toOption, paramName),
      parameters = None,
      validationContext = outputVar.map(ctx.withVariable(_, typingResult)).getOrElse(Valid(ctx)),
      compiledObject = validTypedExpression.map(_.expression),
      expressionType = Some(typingResult)
    )

    (validTypedExpression, nodeCompilation)

  }

}

object BuiltInNodeCompiler {

  private[nodecompilation] val unusedCompiledExpression: CompiledExpression = new CompiledExpression {
    override val language: Expression.Language = Expression.Language.Spel
    override val original: String              = ""

    override def evaluate[T](ctx: Context, globals: Map[String, Any]): T =
      null.asInstanceOf[T]
  }

  private def typedExprToTypingResult(expr: Option[TypedExpression]) = {
    expr.map(_.returnType).getOrElse(Unknown)
  }

  private def typedExprToTypingInfo(expr: Option[TypedExpression], parameterName: ParameterName) = {
    expr.map(te => (parameterName.value, te.typingInfo)).toMap
  }

  private def combineErrors[T](
      compilationResult: NodeCompilationResult[T],
      additionalValidationResult: ValidatedNel[ProcessCompilationError, Unit]
  ): NodeCompilationResult[T] = {
    val newCompiledObject = (compilationResult.compiledObject, additionalValidationResult).mapN { case (result, _) =>
      result
    }
    compilationResult.copy(compiledObject = newCompiledObject)
  }

}
