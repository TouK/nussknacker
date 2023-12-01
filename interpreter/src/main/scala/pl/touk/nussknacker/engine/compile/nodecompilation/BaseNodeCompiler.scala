package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.Validated.{Invalid, Valid, valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits.{catsSyntaxSemigroup, catsSyntaxTuple2Semigroupal, toFoldableOps, toTraverseOps}
import cats.instances.list._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomParameterValidationError
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.definition.{
  MandatoryParameterValidator,
  NotNullParameterValidator,
  ParameterValidator
}
import pl.touk.nussknacker.engine.api.expression.{ExpressionTypingInfo, TypedExpression}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.compile._
import pl.touk.nussknacker.engine.compile.nodecompilation.BaseNodeCompiler.combineErrors
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler.NodeCompilationResult
import pl.touk.nussknacker.engine.compiledgraph
import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam.TypedParameter
import pl.touk.nussknacker.engine.graph.expression.NodeExpressionId.DefaultExpressionId
import pl.touk.nussknacker.engine.graph.expression._
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node._

class BaseNodeCompiler(objectParametersExpressionCompiler: ExpressionCompiler) {

  def compileVariable(variable: Variable, ctx: ValidationContext)(
      implicit nodeId: NodeId
  ): NodeCompilationResult[expression.Expression] = {
    val (expressionCompilation, nodeCompilation) =
      compileExpression(
        variable.value,
        ctx,
        expectedType = Unknown,
        outputVar = Some(OutputVar.variable(variable.varName))
      )

    val additionalValidationResult: ValidatedNel[ProcessCompilationError, Unit] =
      ValidationAdapter.validateMaybeVariable(expressionCompilation.typedExpression, DefaultExpressionId)

    combineErrors(nodeCompilation, additionalValidationResult)
  }

  def compileFilter(filter: Filter, ctx: ValidationContext)(
      implicit nodeId: NodeId
  ): NodeCompilationResult[expression.Expression] = {
    val (expressionCompilation, nodeCompilation) =
      compileExpression(
        filter.expression,
        ctx,
        expectedType = Typed[Boolean],
        outputVar = None
      )

    val additionalValidationResult =
      ValidationAdapter.validateMaybeBoolean(expressionCompilation.typedExpression, DefaultExpressionId)

    combineErrors(nodeCompilation, additionalValidationResult)
  }

  def compileSwitch(
      expressionRaw: Option[(String, Expression)],
      choices: List[(String, Expression)],
      ctx: ValidationContext
  )(
      implicit nodeId: NodeId
  ): NodeCompilationResult[(Option[expression.Expression], List[expression.Expression])] = {

    // the frontend uses empty string to delete deprecated expression.
    val expression = expressionRaw.filterNot(_._1.isEmpty)

    val expressionCompilation = expression.map { case (output, expression) =>
      compileExpression(
        expression,
        ctx,
        Unknown,
        NodeExpressionId.DefaultExpressionId,
        Some(OutputVar.switch(output))
      )._2
    }
    val objExpression = expressionCompilation.map(_.compiledObject).sequence

    val caseCtx = expressionCompilation.flatMap(_.validationContext.toOption).getOrElse(ctx)

    val (additionalValidations, caseExpressions) = choices.map { case (outEdge, caseExpr) =>
      val (expressionCompilation, nodeCompilation) =
        compileExpression(caseExpr, caseCtx, Typed[Boolean], outEdge, None)
      val typedExpression = expressionCompilation.typedExpression
      val validation      = ValidationAdapter.validateMaybeBoolean(typedExpression, outEdge)
      val caseExpression  = nodeCompilation
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

  case class CompiledIndexedRecordField(
      field: compiledgraph.variable.Field,
      index: Int,
      typedExpression: TypedExpression
  )

  def compileFields(
      fields: List[pl.touk.nussknacker.engine.graph.variable.Field],
      ctx: ValidationContext,
      outputVar: Option[OutputVar]
  )(implicit nodeId: NodeId): NodeCompilationResult[List[compiledgraph.variable.Field]] = {

    val (compiledRecord, indexedFields) = {
      val compiledFields = fields.zipWithIndex.map { case (field, index) =>
        val compiledField = objectParametersExpressionCompiler
          .compile(field.expression, Some(node.recordValueFieldName(index)), ctx, Unknown)
          .map(result =>
            CompiledIndexedRecordField(compiledgraph.variable.Field(field.name, result.expression), index, result)
          )
        val indexedKeys = RecordValidator.IndexedFieldKey(field.name, index)
        (compiledField, indexedKeys)
      }
      (compiledFields.map(_._1).sequence, compiledFields.map(_._2))
    }

    val typedObject = compiledRecord match {
      case Valid(fields) =>
        TypedObjectTypingResult(fields.map(f => (f.field.name, typedExprToTypingResult(Some(f.typedExpression)))).toMap)
      case Invalid(_) => Unknown
    }

    val fieldsTypingInfo: Map[String, ExpressionTypingInfo] = compiledRecord match {
      case Valid(fields) => fields.flatMap(a => typingExprToTypingInfo(Some(a.typedExpression), a.field.name)).toMap
      case Invalid(_)    => Map.empty
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

  private def typedExprToTypingResult(expr: Option[TypedExpression]) = {
    expr.map(_.returnType).getOrElse(Unknown)
  }

  private def typingExprToTypingInfo(expr: Option[TypedExpression], fieldName: String) = {
    expr.map(te => (fieldName, te.typingInfo)).toMap
  }

  case class ExpressionCompilation(
      fieldName: String,
      typedExpression: ValidatedNel[ProcessCompilationError, TypedExpression],
  ) {

    val typingResult: TypingResult =
      typedExprToTypingResult(typedExpression.toOption)

    val expressionTypingInfo: Map[String, ExpressionTypingInfo] =
      typingExprToTypingInfo(typedExpression.toOption, fieldName)
  }

  private def compileExpression(
      expr: Expression,
      ctx: ValidationContext,
      expectedType: TypingResult,
      fieldName: String = DefaultExpressionId,
      outputVar: Option[OutputVar]
  )(
      implicit nodeId: NodeId
  ): (ExpressionCompilation, NodeCompilationResult[expression.Expression]) = {
    val expressionCompilation = objectParametersExpressionCompiler
      .compile(expr, Some(fieldName), ctx, expectedType)
      .map(expr => ExpressionCompilation(fieldName, Valid(expr)))
      .valueOr(err => ExpressionCompilation(fieldName, Invalid(err)))

    val nodeCompilation: NodeCompilationResult[expression.Expression] = NodeCompilationResult(
      expressionTypingInfo = typingExprToTypingInfo(expressionCompilation.typedExpression.toOption, fieldName),
      parameters = None,
      validationContext = outputVar.map(ctx.withVariable(_, expressionCompilation.typingResult)).getOrElse(Valid(ctx)),
      compiledObject = expressionCompilation.typedExpression.map(_.expression),
      expressionType = Some(expressionCompilation.typingResult)
    )

    (expressionCompilation, nodeCompilation)

  }

  object RecordValidator {

    case class IndexedFieldKey(key: String, index: Int)

    def validate(
        compiledRecord: ValidatedNel[PartSubGraphCompilationError, List[CompiledIndexedRecordField]],
        indexedFields: List[IndexedFieldKey]
    )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit] = {
      val emptyValuesResult: ValidatedNel[ProcessCompilationError, Unit] = compiledRecord match {
        case Valid(a)   => validateRecordEmptyValues(a)
        case Invalid(_) => valid(())
      }
      val uniqueKeysResult = validateUniqueKeys(indexedFields)
      (emptyValuesResult, uniqueKeysResult).mapN((_, _) => ())
    }

    private def validateUniqueKeys(
        fields: List[IndexedFieldKey]
    )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit] = {
      fields.map { field =>
        validateUniqueRecordKey(fields.map(_.key), field.key, recordKeyFieldName(field.index))
      }.combineAll
    }

    private def validateRecordEmptyValues(
        fields: List[CompiledIndexedRecordField]
    )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit] = {
      fields.map { field =>
        ValidationAdapter.validateMaybeVariable(Valid(field.typedExpression), recordValueFieldName(field.index))
      }.combineAll
    }

    private def validateUniqueRecordKey(allValues: Seq[String], requiredToBeUniqueValue: String, fieldName: String)(
        implicit nodeId: NodeId
    ) = {
      if (allValues.count(_ == requiredToBeUniqueValue) <= 1) {
        valid(())
      } else
        {
          Invalid(
            CustomParameterValidationError(
              "The key of a record has to be unique",
              "Record key not unique",
              fieldName,
              nodeId.id
            )
          )
        }.toValidatedNel
    }

  }

  private object ValidationAdapter {

    def validateMaybeBoolean(
        expression: ValidatedNel[ProcessCompilationError, TypedExpression],
        fieldName: String
    )(
        implicit nodeId: NodeId
    ): ValidatedNel[PartSubGraphCompilationError, Unit] = {
      validateOrValid(NotNullParameterValidator, expression, fieldName)
    }

    def validateMaybeVariable(
        expression: ValidatedNel[ProcessCompilationError, TypedExpression],
        fieldName: String
    )(
        implicit nodeId: NodeId
    ): ValidatedNel[PartSubGraphCompilationError, Unit] = {
      validateOrValid(MandatoryParameterValidator, expression, fieldName)
    }

    private def validateOrValid(
        validator: ParameterValidator,
        expression: ValidatedNel[ProcessCompilationError, TypedExpression],
        fieldName: String
    )(implicit nodeId: NodeId): ValidatedNel[PartSubGraphCompilationError, Unit] = {
      expression
        .map { expr =>
          Validations
            .validate(List(validator), TypedParameter(fieldName, expr))
            .map(_ => ())
        }
        .getOrElse(valid(()))
    }

  }

}

object BaseNodeCompiler {

  private def combineErrors[T](
      compilationResult: NodeCompilationResult[T],
      additionalValidationResult: ValidatedNel[ProcessCompilationError, Unit]
  ): NodeCompilationResult[T] = {
    val newCompiledObject: ValidatedNel[ProcessCompilationError, T] =
      additionalValidationResult match {
        case Invalid(validationErrors) =>
          compilationResult.compiledObject match {
            case Invalid(compilationErrors) =>
              Invalid(compilationErrors |+| validationErrors)
            case _ =>
              Invalid(validationErrors)
          }
        case Valid(_) =>
          compilationResult.compiledObject
      }
    compilationResult.copy(compiledObject = newCompiledObject)
  }

}
