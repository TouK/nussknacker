package pl.touk.nussknacker.ui.process.test.testcase

import cats.data.{NonEmptyList, ValidatedNel}
import cats.data.Validated.{Invalid, Valid}
import cats.syntax.all._
import enumeratum.{Enum, EnumEntry}
import enumeratum.EnumEntry.LowerCamelcase
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.expression.parse.{CompiledExpression, TypedExpression}
import pl.touk.nussknacker.engine.spel.SpelExtension.SpelExpresion
import pl.touk.nussknacker.engine.test.testcase.{Assertion, TestCase}
import pl.touk.nussknacker.engine.test.testcase.Assertion.{AssertionOperator, ExpressionAssertion, PredicateAssertion}
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.restmodel.validation.ValidationResults.NodeTypingData
import pl.touk.nussknacker.ui.process.test.testcase.AssertionCompilationError.{
  ExpressionAssertionCompilationError,
  PredicateAssertionCompilationError
}
import pl.touk.nussknacker.ui.process.test.testcase.AssertionValidationError.AssertionConfiguredForNotExistingNodesError
import pl.touk.nussknacker.ui.process.test.testcase.CompiledAssertion.{
  CompiledExpressionAssertion,
  CompiledPredicateAssertion
}

class AssertionsCompiler(
    expressionCompiler: ExpressionCompiler,
    globalVariablesPreparer: GlobalVariablesPreparer
) {

  def compile(
      testCase: TestCase,
      scenarioTypingResult: Map[String, NodeTypingData],
      jobData: JobData
  ): ValidatedNel[AssertionError, CompiledAssertions] = {
    testCase.assertions
      .map { case (nodeId, assertions) =>
        compileNodeAssertions(assertions, scenarioTypingResult, jobData)(nodeId)
          .tupleLeft(nodeId)
      }
      .toList
      .sequence
      .map(assertions => CompiledAssertions(assertions.toMap))
  }

  // Returns all compiled assertions or combined compilation errors. It's used to compile test case before performing it.
  private def compileNodeAssertions(
      assertions: List[Assertion],
      nodesTyping: Map[String, NodeTypingData],
      jobData: JobData
  )(implicit nodeId: NodeId): ValidatedNel[AssertionError, List[CompiledAssertion]] = {
    validateTypingExistence(nodesTyping).andThen { nodeTypingData =>
      compileForNode(assertions, nodeTypingData.variableTypes, jobData).sequence
    }
  }

  // For each assertion, returns compiled assertion or compilation errors. We need this, to return errors by assertion.
  def compileForNode(
      assertions: List[Assertion],
      variableTypes: Map[String, TypingResult],
      jobData: JobData
  )(implicit nodeId: NodeId): List[ValidatedNel[AssertionCompilationError, CompiledAssertion]] = {
    val ctx = TestCaseVariables.extendNodeVariablesValidationContext(
      globalVariablesPreparer.prepareValidationContextWithGlobalVariablesOnly(jobData),
      variableTypes
    )
    assertions.map(compileAssertion(ctx, _))
  }

  private def validateTypingExistence(
      nodesTyping: Map[String, NodeTypingData],
  )(implicit nodeId: NodeId): ValidatedNel[AssertionError, NodeTypingData] = {
    nodesTyping
      .get(nodeId.id)
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
  )(implicit nodeId: NodeId): ValidatedNel[AssertionCompilationError, CompiledAssertion] = {
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
  )(implicit nodeId: NodeId): ValidatedNel[ExpressionAssertionCompilationError, CompiledExpressionAssertion] = {
    expressionCompiler
      .compile(
        assertion.expression,
        paramName = None,
        context,
        Typed.typedClass(classOf[AssertionResult])
      )
      .map(e => CompiledExpressionAssertion(e.expression))
      .leftMap(ExpressionAssertionCompilationError(_, assertion, nodeId))
      .toValidatedNel
  }

  private def compilePredicateAssertion(
      context: ValidationContext,
      assertion: PredicateAssertion
  )(implicit nodeId: NodeId): ValidatedNel[PredicateAssertionCompilationError, CompiledPredicateAssertion] = {
    compileExpectedExpression(context, assertion)
      .andThen { expectedExpression =>
        compileActualExpression(context, assertion, expectedExpression.returnType)
          .tupleLeft(expectedExpression)
      }
      .andThen { case (expectedExpression, actualExpression) =>
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

  private def compileExpectedExpression(
      context: ValidationContext,
      assertion: PredicateAssertion
  )(implicit nodeId: NodeId): ValidatedNel[PredicateAssertionCompilationError, TypedExpression] = {
    expressionCompiler
      .compile(
        assertion.expected,
        Some(ParameterName(PredicateAssertionCompilationError.Field.Expected.entryName)),
        context,
        Unknown
      )
      .leftMap(
        PredicateAssertionCompilationError(
          _,
          assertion,
          Some(PredicateAssertionCompilationError.Field.Expected),
          nodeId
        )
      )
      .toValidatedNel
  }

  private def compileActualExpression(
      context: ValidationContext,
      assertion: PredicateAssertion,
      expectedType: TypingResult
  )(implicit nodeId: NodeId): ValidatedNel[PredicateAssertionCompilationError, TypedExpression] = {
    expressionCompiler
      .compile(
        assertion.actual,
        Some(ParameterName(PredicateAssertionCompilationError.Field.Actual.entryName)),
        context,
        expectedType
      )
      .leftMap(
        PredicateAssertionCompilationError(_, assertion, Some(PredicateAssertionCompilationError.Field.Actual), nodeId)
      )
      .toValidatedNel
  }

  private def compileComparisonExpression(
      context: ValidationContext,
      assertion: PredicateAssertion,
  )(implicit nodeId: NodeId): ValidatedNel[PredicateAssertionCompilationError, TypedExpression] = {
    expressionCompiler
      .compile(
        s"${assertion.expected.expression} ${toComparisonOperator(assertion.operator)} ${assertion.actual.expression}".spel,
        paramName = None,
        context,
        Typed[java.lang.Boolean]
      )
      .leftMap(
        PredicateAssertionCompilationError(_, assertion, field = None, nodeId)
      )
      .toValidatedNel
  }

  private def toComparisonOperator(operator: Assertion.AssertionOperator): String = {
    operator match {
      case AssertionOperator.Equals    => "=="
      case AssertionOperator.NotEquals => "!="
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
      nodeId: NodeId
  ) extends AssertionCompilationError

  final case class PredicateAssertionCompilationError(
      errors: NonEmptyList[ProcessCompilationError],
      assertion: PredicateAssertion,
      field: Option[PredicateAssertionCompilationError.Field],
      nodeId: NodeId,
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
