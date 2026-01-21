package pl.touk.nussknacker.ui.process.test.testcase

import cats.data.{NonEmptyList, ValidatedNel}
import cats.data.Validated.{Invalid, Valid}
import cats.syntax.all._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypingResult, Unknown}
import pl.touk.nussknacker.engine.compile.ExpressionCompiler
import pl.touk.nussknacker.engine.test.testcase.{Assertion, TestCase}
import pl.touk.nussknacker.engine.test.testcase.Assertion.{ExpressionAssertion, PredicateAssertion}
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
      .map { case (node, assertions) =>
        compileNodeAssertions(node, assertions, scenarioTypingResult, jobData)
          .tupleLeft(node)
      }
      .toList
      .sequence
      .map(assertions => CompiledAssertions(assertions.toMap))
  }

  // Returns all compiled assertions or combined compilation errors. It's used to compile test case before performing it.
  private def compileNodeAssertions(
      nodeId: NodeId,
      assertions: List[Assertion],
      nodesTyping: Map[String, NodeTypingData],
      jobData: JobData
  ): ValidatedNel[AssertionError, List[CompiledAssertion]] = {
    validateTypingExistence(nodeId, nodesTyping).andThen { nodeTypingData =>
      compileForNode(nodeId, assertions, nodeTypingData.variableTypes, jobData).sequence
    }
  }

  // For each assertion, returns compiled assertion or compilation errors. We need this, to return errors by assertion.
  def compileForNode(
      nodeId: NodeId,
      assertions: List[Assertion],
      variableTypes: Map[String, TypingResult],
      jobData: JobData
  ): List[ValidatedNel[AssertionCompilationError, CompiledAssertion]] = {
    val ctx = TestCaseVariables.extendNodeVariablesValidationContext(
      globalVariablesPreparer.prepareValidationContextWithGlobalVariablesOnly(jobData),
      variableTypes
    )
    assertions.map(compileAssertionExpression(nodeId, ctx, _))
  }

  private def validateTypingExistence(
      nodeId: NodeId,
      nodesTyping: Map[String, NodeTypingData],
  ): ValidatedNel[AssertionError, NodeTypingData] = {
    nodesTyping
      .get(nodeId.id)
      .map(Valid(_))
      .getOrElse(
        Invalid(
          NonEmptyList.one(AssertionConfiguredForNotExistingNodesError(NonEmptyList(nodeId, Nil)))
        )
      )
  }

  private def compileAssertionExpression(
      nodeId: NodeId,
      context: ValidationContext,
      assertion: Assertion
  ): ValidatedNel[AssertionCompilationError, CompiledAssertion] = {
    assertion match {
      case expressionAssertion: ExpressionAssertion =>
        compileExpressionAssertion(nodeId, context, expressionAssertion)
      case predicateAssertion: PredicateAssertion =>
        compilePredicateAssertion(nodeId, context, predicateAssertion)
    }
  }

  private def compileExpressionAssertion(
      nodeId: NodeId,
      context: ValidationContext,
      assertion: ExpressionAssertion,
  ): ValidatedNel[ExpressionAssertionCompilationError, CompiledExpressionAssertion] = {
    expressionCompiler
      .compile(
        assertion.expression,
        paramName = None,
        context,
        Typed.typedClass(classOf[AssertionResult])
      )(nodeId)
      .map(e => CompiledExpressionAssertion(e.expression))
      .leftMap(ExpressionAssertionCompilationError(_, assertion, nodeId))
      .toValidatedNel
  }

  private def compilePredicateAssertion(
      nodeId: NodeId,
      context: ValidationContext,
      assertion: PredicateAssertion
  ): ValidatedNel[PredicateAssertionCompilationError, CompiledPredicateAssertion] = {
    val compiledExpected = expressionCompiler
      .compile(
        assertion.expected,
        Some(ParameterName("expected")),
        context,
        Unknown // For equals, we can assume any of type of expected and actual expressions are fine, but for >=, <, etc. we could also check if both types are comparable.
      )(nodeId)
      .leftMap(
        PredicateAssertionCompilationError(_, assertion, PredicateAssertionCompilationError.ExpectedField, nodeId)
      )
      .toValidatedNel
    val compiledActual = expressionCompiler
      .compile(
        assertion.actual,
        Some(ParameterName("actual")),
        context,
        Unknown
      )(nodeId)
      .leftMap(PredicateAssertionCompilationError(_, assertion, PredicateAssertionCompilationError.ActualField, nodeId))
      .toValidatedNel

    (compiledExpected, compiledActual)
      .mapN { (expected, actual) =>
        CompiledPredicateAssertion(assertion.operator, expected.expression, actual.expression)
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
      field: PredicateAssertionCompilationError.Field,
      nodeId: NodeId,
  ) extends AssertionCompilationError

  object PredicateAssertionCompilationError {
    sealed trait Field
    case object ExpectedField extends Field
    case object ActualField   extends Field
  }

}
