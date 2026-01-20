package pl.touk.nussknacker.ui.process.test.testcase

import pl.touk.nussknacker.engine.api.{Context, ContextId, JobData, NodeId}
import pl.touk.nussknacker.engine.expression.parse.CompiledExpression
import pl.touk.nussknacker.engine.test.testcase.Assertion.AssertionOperator
import pl.touk.nussknacker.engine.testmode.TestProcess.ResultContext
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap
import pl.touk.nussknacker.engine.variables.GlobalVariablesPreparer
import pl.touk.nussknacker.ui.process.test.testcase.CompiledAssertion.CompiledExpressionAssertion

import scala.jdk.CollectionConverters._

class AssertionVerifier(globalVariablesPreparer: GlobalVariablesPreparer) {

  def verify(
      compiledAssertions: CompiledAssertions,
      results: Map[NodeId, List[ResultContext[Any]]],
      jobData: JobData
  ): Map[NodeId, List[AssertionResult]] = {
    compiledAssertions.assertions.map { case (nodeId, assertions) =>
      nodeId -> assertions.map(assertion => verifySingleAssertions(assertion, nodeId, results, jobData))
    }
  }

  private def verifySingleAssertions(
      assertion: CompiledAssertion,
      nodeId: NodeId,
      results: Map[NodeId, List[ResultContext[Any]]],
      jobData: JobData
  ): AssertionResult = {
    val context = prepareEvaluationContext(nodeId, results)
    val globalVariables = TestCaseVariables
      .extendGlobalVariables(
        globalVariablesPreparer.prepareGlobalVariables(jobData)
      )
      .mapValuesNow(_.obj)
    try {
      assertion match {
        case CompiledExpressionAssertion(expression) =>
          evaluateExpressionAssertion(expression, context, globalVariables)
        case CompiledAssertion.CompiledPredicateAssertion(operator, expected, actual) =>
          evaluatePredicateAssertion(operator, expected, actual, context, globalVariables)
      }
    } catch {
      case e: Exception => FailedAssertion(s"Exception during assertion evaluation: ${e.getMessage}")
    }
  }

  private def prepareEvaluationContext(
      nodeId: NodeId,
      results: Map[NodeId, List[ResultContext[Any]]]
  ): Context = {
    val resultsForNode = results
      .getOrElse(nodeId, List.empty)
      .map(_.variables.asJava)
      .asJava
    Context(ContextId.dummy, Map(TestCaseVariables.ContextsNodeVariableName -> resultsForNode))
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
      expected: CompiledExpression,
      actual: CompiledExpression,
      context: Context,
      globalVariables: Map[String, Any]
  ): AssertionResult = {
    val expectedValue = expected.evaluate[Any](context, globalVariables)
    val actualValue   = actual.evaluate[Any](context, globalVariables)
    operator match {
      case AssertionOperator.Equals => AssertionResult.assertEquals(expectedValue, actualValue)
    }
  }

}
