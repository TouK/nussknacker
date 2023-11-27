package pl.touk.nussknacker.engine.compile.nodecompilation

import cats.data.Validated.{Invalid, Valid, valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits.{catsSyntaxTuple2Semigroupal, toFoldableOps, toTraverseOps}
import cats.instances.list._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomParameterValidationError
import pl.touk.nussknacker.engine.api.context._
import pl.touk.nussknacker.engine.api.definition.{
  MandatoryParameterValidator,
  NotNullParameterValidator,
  Parameter,
  ParameterValidator
}
import pl.touk.nussknacker.engine.api.expression.{ExpressionTypingInfo, TypedExpression}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult, Unknown}
import pl.touk.nussknacker.engine.compile._
import pl.touk.nussknacker.engine.compile.nodecompilation.BaseNodeCompiler.combineErrors
import pl.touk.nussknacker.engine.compile.nodecompilation.NodeCompiler.NodeCompilationResult
import pl.touk.nussknacker.engine.compiledgraph.evaluatedparam.TypedParameter
import pl.touk.nussknacker.engine.compiledgraph.variable
import pl.touk.nussknacker.engine.graph.expression.NodeExpressionId.DefaultExpressionId
import pl.touk.nussknacker.engine.graph.expression._
import pl.touk.nussknacker.engine.graph.node
import pl.touk.nussknacker.engine.graph.node._

class BaseNodeCompiler(objectParametersExpressionCompiler: ExpressionCompiler) {

  def compileVariable(variable: Variable, ctx: ValidationContext)(
      implicit nodeId: NodeId
  ): NodeCompilationResult[expression.Expression] = {
    val (expressionCompilation, nodeCompilation) =
      ExpressionCompilerAdapter.compileExpression(
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
      ExpressionCompilerAdapter.compileExpression(
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
      ExpressionCompilerAdapter
        .compileExpression(
          expression,
          ctx,
          Unknown,
          NodeExpressionId.DefaultExpressionId,
          Some(OutputVar.switch(output))
        )
        ._2
    }
    val objExpression = expressionCompilation.map(_.compiledObject.map(Some(_))).getOrElse(Valid(None))

    val caseCtx = expressionCompilation.flatMap(_.validationContext.toOption).getOrElse(ctx)
    val caseExpressions = choices.map { case (outEdge, caseExpr) =>
      ExpressionCompilerAdapter.compileExpression(caseExpr, caseCtx, Typed[Boolean], outEdge, None)._2
    }
    val expressionTypingInfos = caseExpressions
      .map(_.expressionTypingInfo)
      .foldLeft(expressionCompilation.map(_.expressionTypingInfo).getOrElse(Map.empty)) {
        _ ++ _
      }

    val objCases = caseExpressions.map(_.compiledObject).sequence

    val additionalValidations = choices
      .map { case (outEdge, caseExpr) =>
        (outEdge, ExpressionCompilerAdapter.compileExpression(caseExpr, caseCtx, Typed[Boolean], outEdge, None)._1)
      }
      .map { a =>
        ValidationAdapter.validateMaybeBoolean(a._2.typedExpression, a._1)
      }
      .combineAll

    val compilationResult = NodeCompilationResult(
      expressionTypingInfos,
      None,
      expressionCompilation.map(_.validationContext).getOrElse(Valid(ctx)),
      objExpression.product(objCases),
      expressionCompilation.flatMap(_.expressionType)
    )

    combineErrors(compilationResult, additionalValidations)
  }

  def compileFields(
      fields: List[pl.touk.nussknacker.engine.graph.variable.Field],
      ctx: ValidationContext,
      outputVar: Option[OutputVar]
  )(implicit nodeId: NodeId): NodeCompilationResult[List[variable.Field]] = {

    final case class CompiledRecordField(
        field: pl.touk.nussknacker.engine.graph.variable.Field,
        index: Int,
        typedExpression: TypedExpression
    )

    val compliedRecordFields = fields.zipWithIndex.map { case (field, index) =>
      objectParametersExpressionCompiler
        .compile(field.expression, Some(node.recordValueFieldName(index)), ctx, Unknown)
        .map(result => CompiledRecordField(field, index, result))
    }

    val recordValuesCompilationResult = compliedRecordFields.traverse { validatedField =>
      validatedField.andThen { compiledField =>
        Valid(
          ExpressionCompilerAdapter.ExpressionCompilation(
            compiledField.field.name,
            Some(compiledField.typedExpression),
            Valid(variable.Field(compiledField.field.name, compiledField.typedExpression.expression))
          )
        )
      }
    }

    val typedFieldsValidations = compliedRecordFields.map { validatedField =>
      validatedField.map { compiledField =>
        RecordValidator.IndexedField(
          compiledField.field.name,
          compiledField.typedExpression,
          compiledField.index
        )
      }
    }
    val additionalValidationResult = RecordValidator.validateRecord(
      typedFieldsValidations.collect { case Valid(typedField) => typedField }
    )

    val typedObject = recordValuesCompilationResult
      .map { fieldsComp =>
        TypedObjectTypingResult(fieldsComp.map(f => (f.fieldName, f.typingResult)).toMap)
      }
      .valueOr(_ => Unknown)

    val fieldsTypingInfo = recordValuesCompilationResult
      .map { compilations =>
        compilations.flatMap(_.expressionTypingInfo).toMap
      }
      .getOrElse(Map.empty)

    val compiledFields = recordValuesCompilationResult.andThen(_.map(_.validated).sequence)

    val compilationResult = NodeCompilationResult(
      expressionTypingInfo = fieldsTypingInfo,
      parameters = None,
      validationContext = outputVar.map(ctx.withVariable(_, typedObject)).getOrElse(Valid(ctx)),
      compiledObject = compiledFields,
      expressionType = Some(typedObject)
    )

    combineErrors(compilationResult, additionalValidationResult)
  }

  private object ExpressionCompilerAdapter {

    case class ExpressionCompilation[R](
        fieldName: String,
        typedExpression: Option[TypedExpression],
        validated: ValidatedNel[ProcessCompilationError, R]
    ) {

      val typingResult: TypingResult =
        typedExpression.map(_.returnType).getOrElse(Unknown)

      val expressionTypingInfo: Map[String, ExpressionTypingInfo] =
        typedExpression.map(te => (fieldName, te.typingInfo)).toMap
    }

    def compileExpression(
        expr: Expression,
        ctx: ValidationContext,
        expectedType: TypingResult,
        fieldName: String = DefaultExpressionId,
        outputVar: Option[OutputVar]
    )(
        implicit nodeId: NodeId
    ): (ExpressionCompilation[expression.Expression], NodeCompilationResult[expression.Expression]) = {
      val expressionCompilation: ExpressionCompilation[expression.Expression] = objectParametersExpressionCompiler
        .compile(expr, Some(fieldName), ctx, expectedType)
        .map(typedExpr => ExpressionCompilation(fieldName, Some(typedExpr), Valid(typedExpr.expression)))
        .valueOr(err => ExpressionCompilation(fieldName, None, Invalid(err)))

      val nodeCompilation: NodeCompilationResult[expression.Expression] = NodeCompilationResult(
        expressionTypingInfo = expressionCompilation.expressionTypingInfo,
        parameters = None,
        validationContext =
          outputVar.map(ctx.withVariable(_, expressionCompilation.typingResult)).getOrElse(Valid(ctx)),
        compiledObject = expressionCompilation.validated,
        expressionType = Some(expressionCompilation.typingResult)
      )

      (expressionCompilation, nodeCompilation)
    }

  }

  private object RecordValidator {

    case class IndexedField(key: String, value: TypedExpression, index: Int)

    def validateRecord(
        fields: List[IndexedField]
    )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit] = {
      fields.map { field =>
        (
          ValidationAdapter.validateMaybeVariable(Some(field.value), recordValueFieldName(field.index)),
          validateUniqueRecordKey(fields.map(_.key), field.key, recordKeyFieldName(field.index))
        ).mapN { (_, _) => () }
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
        expression: Option[TypedExpression],
        fieldName: String
    )(
        implicit nodeId: NodeId
    ): Validated[NonEmptyList[PartSubGraphCompilationError], Unit] = {
      validateOrValid(Typed[Boolean], NotNullParameterValidator, expression, fieldName)
    }

    def validateMaybeVariable(
        expression: Option[TypedExpression],
        fieldName: String
    )(
        implicit nodeId: NodeId
    ): Validated[NonEmptyList[PartSubGraphCompilationError], Unit] = {
      validateOrValid(Unknown, MandatoryParameterValidator, expression, fieldName)
    }

    private def validateOrValid(
        expectedType: TypingResult,
        validator: ParameterValidator,
        expression: Option[TypedExpression],
        fieldName: String
    )(implicit nodeId: NodeId): Validated[NonEmptyList[PartSubGraphCompilationError], Unit] = {
      expression
        .map { expr =>
          Validations
            .validate(Parameter("stub", expectedType, List(validator)), (TypedParameter(fieldName, expr), ()))
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
        case Validated.Invalid(validationErrors) =>
          compilationResult.compiledObject match {
            case Validated.Invalid(compilationErrors) =>
              Validated.Invalid(compilationErrors ++ validationErrors.toList)
            case _ =>
              Validated.Invalid(validationErrors)
          }
        case Validated.Valid(_) =>
          compilationResult.compiledObject
      }
    compilationResult.copy(compiledObject = newCompiledObject)
  }

}
