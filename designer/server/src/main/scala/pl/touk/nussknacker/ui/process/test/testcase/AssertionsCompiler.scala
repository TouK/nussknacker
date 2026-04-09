package pl.touk.nussknacker.ui.process.test.testcase

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.data.Validated.{Invalid, Valid}
import cats.syntax.all._
import enumeratum.{Enum, EnumEntry}
import enumeratum.EnumEntry.LowerCamelcase
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.ExpressionParserCompilationError
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.compile.{ExpressionCompiler, NodeTypingInfo}
import pl.touk.nussknacker.engine.expression.parse.TypedExpression
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.test.testcase.{Assertion, TestCase}
import pl.touk.nussknacker.engine.test.testcase.Assertion.{AssertionOperator, ExpressionAssertion, PredicateAssertion}
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.ui.process.test.testcase.AssertionCompilationError.{
  ExpressionAssertionCompilationError,
  PredicateAssertionCompilationError
}
import pl.touk.nussknacker.ui.process.test.testcase.AssertionValidationError.AssertionConfiguredForNotExistingNodesError
import pl.touk.nussknacker.ui.process.test.testcase.CompiledAssertion.{
  CompiledExpressionAssertion,
  CompiledPredicateAssertion,
  EmptyCompiledAssertion
}

class AssertionsCompiler(
    expressionCompiler: ExpressionCompiler,
    globalVariablesPreparer: GlobalVariablesPreparer
) {

  def compile(
      testCase: TestCase,
      scenarioTypingResult: Map[String, NodeTypingInfo],
      jobData: JobData,
      nodeNamesById: Map[NodeId, NodeName],
  ): ValidatedNel[AssertionError, CompiledAssertions] = {
    testCase.assertions.toList
      .map { case (nodeId, assertions) =>
        nodeNamesById
          .get(nodeId)
          .map { nodeName =>
            compileNodeAssertions(assertions, scenarioTypingResult, jobData)(nodeId, nodeName)
              .tupleLeft(nodeId)
          }
          .getOrElse(
            Invalid(NonEmptyList.one(AssertionConfiguredForNotExistingNodesError(NonEmptyList.one(nodeId))))
          )
      }
      .sequence
      .map(assertions => CompiledAssertions(assertions.toMap))
  }

  // Returns all compiled assertions or combined compilation errors. It's used to compile test case before performing it.
  private def compileNodeAssertions(
      assertions: List[Assertion],
      nodesTyping: Map[String, NodeTypingInfo],
      jobData: JobData
  )(implicit nodeId: NodeId, nodeName: NodeName): ValidatedNel[AssertionError, List[CompiledAssertion]] = {
    validateTypingExistence(nodesTyping).andThen { nodeTypingInfo =>
      compileForNode(
        assertions,
        nodeTypingInfo.inputValidationContext.localVariables,
        nodeTypingInfo.outputValidationContext.map(_.localVariables).getOrElse(Map.empty),
        jobData,
      ).sequence
    }
  }

  // For each assertion, returns compiled assertion or compilation errors. We need this, to return errors by assertion.
  def compileForNode(
      assertions: List[Assertion],
      inputVariableTypes: Map[String, TypingResult],
      outputVariableTypes: Map[String, TypingResult],
      jobData: JobData,
  )(implicit nodeId: NodeId, nodeName: NodeName): List[ValidatedNel[AssertionCompilationError, CompiledAssertion]] = {
    val ctx = TestCaseVariables.extendNodeVariablesValidationContext(
      globalVariablesPreparer.prepareValidationContextWithGlobalVariablesOnly(jobData),
      inputVariableTypes,
      outputVariableTypes
    )
    assertions.map(compileAssertion(ctx, _))
  }

  private def validateTypingExistence(
      nodesTyping: Map[String, NodeTypingInfo],
  )(implicit nodeId: NodeId): ValidatedNel[AssertionError, NodeTypingInfo] = {
    nodesTyping
      .get(nodeId.value)
      .map(Valid(_))
      .getOrElse(
        Invalid(
          NonEmptyList.one(AssertionConfiguredForNotExistingNodesError(NonEmptyList(nodeId, Nil)))
        )
      )
  }

  private def compileAssertion(
      context: ValidationContext,
      assertion: Assertion
  )(implicit nodeId: NodeId, nodeName: NodeName): ValidatedNel[AssertionCompilationError, CompiledAssertion] = {
    assertion match {
      case expressionAssertion: ExpressionAssertion =>
        compileExpressionAssertion(context, expressionAssertion)
      case predicateAssertion: PredicateAssertion =>
        compilePredicateAssertion(context, predicateAssertion)
    }
  }

  private def compileExpressionAssertion(
      context: ValidationContext,
      assertion: ExpressionAssertion,
  )(
      implicit nodeId: NodeId,
      nodeName: NodeName
  ): ValidatedNel[ExpressionAssertionCompilationError, CompiledExpressionAssertion] = {
    expressionCompiler
      .compile(
        assertion.expression,
        paramName = None,
        context,
        Typed.typedClass(classOf[AssertionResult])
      )
      .map(e => CompiledExpressionAssertion(e.expression))
      .leftMap(ExpressionAssertionCompilationError(_, assertion, nodeId, nodeName))
      .toValidatedNel
  }

  private def compilePredicateAssertion(
      context: ValidationContext,
      assertion: PredicateAssertion
  )(
      implicit nodeId: NodeId,
      nodeName: NodeName
  ): ValidatedNel[PredicateAssertionCompilationError, CompiledAssertion] = {
    val expectedCompiled = compileExpectedExpression(context, assertion)
    val actualCompiled   = compileActualExpression(context, assertion)
    (expectedCompiled, actualCompiled).tupled.andThen { case (expectedExpression, actualExpression) =>
      val emptyAssertion =
        expectedExpression.expression.original.isBlank || actualExpression.expression.original.isBlank
      if (emptyAssertion) {
        EmptyCompiledAssertion.validNel
      } else {
        compileComparisonExpression(context, assertion)
          .map { comparisonExpression =>
            CompiledPredicateAssertion(
              operator = assertion.operator,
              expectedExpression = expectedExpression.expression,
              actualExpression = actualExpression.expression,
              comparisonExpression = comparisonExpression.expression
            )
          }
      }
    }
  }

  private def compileExpectedExpression(
      context: ValidationContext,
      assertion: PredicateAssertion
  )(implicit nodeId: NodeId, nodeName: NodeName): ValidatedNel[PredicateAssertionCompilationError, TypedExpression] = {
    expressionCompiler
      .compile(
        assertion.expected,
        Some(ParameterName(PredicateAssertionCompilationError.Field.Expected.entryName)),
        context,
        expectedType = Unknown
      )
      .leftMap(
        PredicateAssertionCompilationError(
          _,
          assertion,
          PredicateAssertionCompilationError.Field.Expected,
          nodeId,
          nodeName
        )
      )
      .toValidatedNel
  }

  private def compileActualExpression(
      context: ValidationContext,
      assertion: PredicateAssertion,
  )(implicit nodeId: NodeId, nodeName: NodeName): ValidatedNel[PredicateAssertionCompilationError, TypedExpression] = {
    expressionCompiler
      .compile(
        assertion.actual,
        Some(ParameterName(PredicateAssertionCompilationError.Field.Actual.entryName)),
        context,
        expectedType = Unknown
      )
      .leftMap(
        PredicateAssertionCompilationError(
          _,
          assertion,
          PredicateAssertionCompilationError.Field.Actual,
          nodeId,
          nodeName
        )
      )
      .toValidatedNel
  }

  private def compileComparisonExpression(
      context: ValidationContext,
      assertion: PredicateAssertion,
  )(implicit nodeId: NodeId, nodeName: NodeName): ValidatedNel[PredicateAssertionCompilationError, TypedExpression] = {
    expressionCompiler
      .compile(
        // Compiling the below expression can result in errors like: Operator '==' used with not comparable types: String and Integer
        // If we need to report more user-friendly error (without: Operator '=='), we can use CommonSupertypeFinder here to check if types are comparable before compiling.
        buildComparisonExpression(assertion.operator, assertion.expected.expression, assertion.actual.expression).spel,
        // See comment below - we report errors on actual field.
        paramName = Some(ParameterName(PredicateAssertionCompilationError.Field.Actual.entryName)),
        context,
        expectedType = Typed[java.lang.Boolean]
      )
      .leftMap { errors =>
        PredicateAssertionCompilationError(
          // Clear the error details, including the position of the error in the comparison expression.
          // Since this expression is never shown to the user, highlighting part of the actual expression at the wrong position would be confusing.
          clearErrorsDetails(errors),
          assertion,
          // If comparison expression fails to compile because of uncomparable types, we report the error on actual field.
          PredicateAssertionCompilationError.Field.Actual,
          nodeId,
          nodeName
        )
      }
      .toValidatedNel
  }

  private def buildComparisonExpression(
      operator: Assertion.AssertionOperator,
      expected: String,
      actual: String
  ): String = {
    operator match {
      case AssertionOperator.Equals =>
        s"($actual) == ($expected)"
      case AssertionOperator.NotEquals =>
        s"($actual) != ($expected)"
      case AssertionOperator.GreaterThan =>
        s"($actual) != null && ($expected) != null && ($actual) > ($expected)"
      case AssertionOperator.LessThan =>
        s"($actual) != null && ($expected) != null && ($actual) < ($expected)"
      case AssertionOperator.GreaterThanOrEqual =>
        s"($actual) != null && ($expected) != null && ($actual) >= ($expected)"
      case AssertionOperator.LessThanOrEqual =>
        s"($actual) != null && ($expected) != null && ($actual) <= ($expected)"
      case AssertionOperator.HasSize =>
        s"($actual) != null && ($expected) != null && ($actual).size() == ($expected)"
      case AssertionOperator.Contains =>
        s"($actual) != null && ($expected) != null && ($actual).contains(($expected))"
      case AssertionOperator.Matches =>
        s"($actual) != null && ($expected) != null && ($actual) matches ($expected)"
    }
  }

  private def clearErrorsDetails(
      errors: NonEmptyList[ProcessCompilationError]
  ): NonEmptyList[ProcessCompilationError] = {
    errors.map {
      case e: ExpressionParserCompilationError =>
        e.copy(details = None)
      case other =>
        other
    }
  }

}

sealed trait AssertionError

sealed trait AssertionValidationError extends AssertionError

object AssertionValidationError {
  final case class AssertionConfiguredForNotExistingNodesError(notExistingNodeIds: NonEmptyList[NodeId])
      extends AssertionValidationError
}

sealed trait AssertionCompilationError extends AssertionError

object AssertionCompilationError {

  final case class ExpressionAssertionCompilationError(
      errors: NonEmptyList[ProcessCompilationError],
      assertion: ExpressionAssertion,
      nodeId: NodeId,
      nodeName: NodeName
  ) extends AssertionCompilationError

  final case class PredicateAssertionCompilationError(
      errors: NonEmptyList[ProcessCompilationError],
      assertion: PredicateAssertion,
      field: PredicateAssertionCompilationError.Field,
      nodeId: NodeId,
      nodeName: NodeName,
  ) extends AssertionCompilationError

  object PredicateAssertionCompilationError {

    sealed trait Field extends EnumEntry with LowerCamelcase

    object Field extends Enum[Field] {
      case object Expected extends Field
      case object Actual   extends Field

      override def values: IndexedSeq[Field] = findValues
    }

  }

}
