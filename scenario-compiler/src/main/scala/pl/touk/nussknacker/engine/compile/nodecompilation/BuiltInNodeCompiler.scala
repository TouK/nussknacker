package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import cats.implicits.{catsSyntaxTuple2Semigroupal, toFoldableOps, toTraverseOps}
import cats.instances.list._
import pl.touk.nussknacker.engine.api.{CompiledExpression, NodeId}
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.expression.ExpressionTypingInfo
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.compile._
import pl.touk.nussknacker.engine.compile.nodecompilation.BaseComponentValidationHelper._
import pl.touk.nussknacker.engine.compile.nodecompilation.BuiltInNodeCompiler._
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler.NodeCompilationResult
import pl.touk.nussknacker.engine.compiledgraph
import pl.touk.nussknacker.engine.expression.parse.TypedExpression
import pl.touk.nussknacker.engine.graph.expression.NodeExpressionId.DefaultExpressionIdParamName
import pl.touk.nussknacker.engine.graph.expression._
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node._

class BuiltInNodeCompiler(expressionCompiler: ExpressionCompiler) {

  def compileVariable(variable: Variable, ctx: ValidationContext)(
      implicit nodeId: NodeId
  ): NodeCompilationResult[CompiledExpression] = {
    val (validTypedExpression, nodeCompilation) =
      compileExpression(
        variable.value,
        ctx,
        expectedType = Unknown,
        paramName = DefaultExpressionIdParamName,
        outputVar = Some(OutputVar.variable(variable.varName))
      )

    val additionalValidationResult = validateVariableValue(validTypedExpression, DefaultExpressionIdParamName)

    combineErrors(nodeCompilation, additionalValidationResult)
  }

  def compileFilter(filter: Filter, ctx: ValidationContext)(
      implicit nodeId: NodeId
  ): NodeCompilationResult[CompiledExpression] = {
    val (validTypedExpression, nodeCompilation) =
      compileExpression(
        filter.expression,
        ctx,
        expectedType = Typed[Boolean],
        paramName = DefaultExpressionIdParamName,
        outputVar = None
      )

    val additionalValidationResult = validateBoolean(validTypedExpression, DefaultExpressionIdParamName)

    combineErrors(nodeCompilation, additionalValidationResult)
  }

  def compileSwitch(
      expressionRaw: Option[(String, Expression)],
      choices: List[(String, Expression)],
      ctx: ValidationContext
  )(
      implicit nodeId: NodeId
  ): NodeCompilationResult[(Option[CompiledExpression], List[CompiledExpression])] = {

    // the frontend uses empty string to delete deprecated expression.
    val expression = expressionRaw.filterNot(_._1.isEmpty)

    val expressionCompilation = expression.map { case (output, expression) =>
      compileExpression(
        expr = expression,
        ctx = ctx,
        expectedType = Unknown,
        paramName = NodeExpressionId.DefaultExpressionIdParamName,
        outputVar = Some(OutputVar.switch(output))
      )._2
    }
    val objExpression = expressionCompilation.map(_.compiledObject).sequence

    val caseCtx = expressionCompilation.flatMap(_.validationContext.toOption).getOrElse(ctx)

    val (additionalValidations, caseExpressions) = choices.map { case (outEdge, caseExpr) =>
      val outEdgeParamName = ParameterName(outEdge)
      val (validTypedExpression, nodeCompilation) =
        compileExpression(caseExpr, caseCtx, Typed[Boolean], outEdgeParamName, None)
      val validation     = validateBoolean(validTypedExpression, outEdgeParamName)
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
      expressionCompilation.map(_.validationContext).getOrElse(Valid(ctx)),
      objExpression.product(objCases),
      expressionCompilation.flatMap(_.expressionType)
    )

    combineErrors(compilationResult, additionalValidations.combineAll)
  }

  def compileFields(
      fields: List[pl.touk.nussknacker.engine.graph.variable.Field],
      ctx: ValidationContext,
      outputVar: Option[OutputVar]
  )(implicit nodeId: NodeId): NodeCompilationResult[List[compiledgraph.variable.Field]] = {

    val (compiledRecord, indexedFields) = {
      val compiledFields = fields.zipWithIndex.map { case (field, index) =>
        val compiledField = expressionCompiler
          .compile(field.expression, Some(ParameterName(node.recordValueFieldName(index))), ctx, Unknown)
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
      validationContext = outputVar.map(ctx.withVariable(_, typedObject)).getOrElse(Valid(ctx)),
      compiledObject = compiledFields,
      expressionType = Some(typedObject)
    )

    val additionalValidationResult = RecordValidator.validate(compiledRecord, indexedFields)

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
