package pl.touk.nussknacker.ui.process.test.testcase

import pl.touk.nussknacker.engine.api.{Context, ContextId, JobData, NodeId}
import pl.touk.nussknacker.engine.expression.parse.CompiledExpression
import pl.touk.nussknacker.engine.test.testcase.Assertion.AssertionOperator
import pl.touk.nussknacker.engine.testmode.TestProcess.{ExternalServiceInvocationResult, NodeTransition, ResultContext}
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.ui.process.test.testcase.CompiledAssertion.{
  CompiledExpressionAssertion,
  CompiledPredicateAssertion,
  EmptyCompiledAssertion
}

import java.util
import scala.jdk.CollectionConverters._

class AssertionVerifier(globalVariablesPreparer: GlobalVariablesPreparer) {

  import AssertionVerifier.TestResults

  def verify(
      compiledAssertions: CompiledAssertions,
      testResults: TestResults,
      jobData: JobData
  ): Map[NodeId, List[AssertionResult]] = {
    compiledAssertions.assertions.map { case (nodeId, assertions) =>
      nodeId -> assertions.map(assertion => verifySingleAssertions(assertion, nodeId, testResults, jobData))
    }
  }

  private def verifySingleAssertions(
      assertion: CompiledAssertion,
      nodeId: NodeId,
      testResults: TestResults,
      jobData: JobData
  ): AssertionResult = {
    val context = prepareEvaluationContext(nodeId, testResults)
    val globalVariables = TestCaseVariables
      .extendGlobalVariables(
        globalVariablesPreparer.prepareGlobalVariables(jobData)
      )
      .mapValuesNow(_.obj)
    try {
      assertion match {
        case CompiledExpressionAssertion(expression) =>
          evaluateExpressionAssertion(expression, context, globalVariables)
        case CompiledPredicateAssertion(operator, expectedExpression, actualExpression, comparisonExpression) =>
          evaluatePredicateAssertion(
            operator,
            expectedExpression,
            actualExpression,
            comparisonExpression,
            context,
            globalVariables
          )
        case EmptyCompiledAssertion =>
          SuccessfulAssertion
      }
    } catch {
      case e: Exception => FailedAssertion(s"Exception during assertion evaluation: ${e.getMessage}")
    }
  }

  private def prepareEvaluationContext(
      nodeId: NodeId,
      testResults: TestResults
  ): Context = {
    Context(
      ContextId.dummy,
      Map(
        TestCaseVariables.RecordsNodeVariableName -> prepareEvaluationContextForIncomingRecords(nodeId, testResults),
        TestCaseVariables.OutgoingRecordsNodeVariableName -> prepareEvaluationContextForOutgoingRecords(
          nodeId,
          testResults
        ),
      )
    )
  }

  private def prepareEvaluationContextForIncomingRecords(
      nodeId: NodeId,
      testResults: TestResults
  ): util.List[util.Map[String, Any]] = {
    testResults.originalNodeResults
      .getOrElse(nodeId, List.empty)
      .map(_.variables.asJava)
      .asJava
  }

  private def prepareEvaluationContextForOutgoingRecords(
      nodeId: NodeId,
      testResults: TestResults
  ): util.List[util.Map[String, Any]] = {
    val transitionResults = testResults.originalNodeTransitionResults
      .collect { case (NodeTransition(sourceNodeId, _), results) if sourceNodeId == nodeId => results }
      .flatten
      .map(_.variables.asJava)
      .toList
    if (transitionResults.nonEmpty) {
      transitionResults.asJava
    } else {
      // For ending nodes (sinks), use external service invocation results as outgoing records.
      // Each record contains the sink's displayable output value (as produced by SinkDisplayableValue.asDisplayableValue).
      testResults.originalExternalServiceInvocationResults
        .getOrElse(nodeId, List.empty)
        .map(invocation => java.util.Collections.singletonMap(invocation.name, invocation.value))
        .asJava
    }
  }

  private def evaluateExpressionAssertion(
      expression: CompiledExpression,
      context: Context,
      globalVariables: Map[String, Any]
  ): AssertionResult = {
    expression.evaluate[AssertionResult](context, globalVariables) match {
      case null                             => FailedAssertion("Assertion result can't be null")
      case assertionResult: AssertionResult => assertionResult
    }
  }

  private def evaluatePredicateAssertion(
      operator: AssertionOperator,
      expectedExpression: CompiledExpression,
      actualExpression: CompiledExpression,
      comparisonExpression: CompiledExpression,
      context: Context,
      globalVariables: Map[String, Any]
  ): AssertionResult = {
    val comparisonResult = comparisonExpression.evaluate[Boolean](context, globalVariables)
    if (comparisonResult) {
      SuccessfulAssertion
    } else {
      val expectedValue = expectedExpression.evaluate[Any](context, globalVariables)
      val actualValue   = actualExpression.evaluate[Any](context, globalVariables)
      operator match {
        case AssertionOperator.Equals =>
          AssertionResult.produceFailedEqualsAssertion(expectedValue, actualValue)
        case AssertionOperator.NotEquals =>
          AssertionResult.produceFailedNotEqualsAssertion(expectedValue)
        case AssertionOperator.GreaterThan =>
          AssertionResult.produceFailedComparisonAssertion(">", expectedValue, actualValue)
        case AssertionOperator.LessThan =>
          AssertionResult.produceFailedComparisonAssertion("<", expectedValue, actualValue)
        case AssertionOperator.GreaterThanOrEqual =>
          AssertionResult.produceFailedComparisonAssertion(">=", expectedValue, actualValue)
        case AssertionOperator.LessThanOrEqual =>
          AssertionResult.produceFailedComparisonAssertion("<=", expectedValue, actualValue)
        case AssertionOperator.Contains =>
          AssertionResult.produceFailedContainsAssertion(expectedValue, actualValue)
        case AssertionOperator.Matches =>
          AssertionResult.produceFailedMatchesAssertion(expectedValue, actualValue)
        case AssertionOperator.HasSize =>
          AssertionResult.produceFailedHasSizeAssertion(expectedValue, actualValue)
      }
    }
  }

}

object AssertionVerifier {

  final case class TestResults(
      originalNodeResults: Map[NodeId, List[ResultContext[Any]]],
      originalNodeTransitionResults: Map[NodeTransition, List[ResultContext[Any]]],
      originalExternalServiceInvocationResults: Map[NodeId, List[ExternalServiceInvocationResult[Any]]],
  )

}
