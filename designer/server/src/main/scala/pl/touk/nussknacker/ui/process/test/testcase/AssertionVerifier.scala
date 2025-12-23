package pl.touk.nussknacker.ui.process.test.testcase

import pl.touk.nussknacker.engine.api.{Context, ContextId, JobData, NodeId}
import pl.touk.nussknacker.engine.testmode.TestProcess.ResultContext
import pl.touk.nussknacker.engine.util.Implicits.RichScalaMap

import scala.jdk.CollectionConverters._

class AssertionVerifier(testCaseGlobalVariablesPreparer: TestCaseGlobalVariablesPreparer) {

  def verify(
      testCase: CompiledAssertions,
      results: Map[NodeId, List[ResultContext[Any]]],
      jobData: JobData
  ): Map[NodeId, List[AssertionResult]] = {
    testCase.assertions.map { case (nodeId, assertions) =>
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
    val globalVariables = testCaseGlobalVariablesPreparer
      .prepareGlobalVariables(jobData)
      .mapValuesNow(_.obj)
    try {
      assertion.expression.evaluate[AssertionResult](context, globalVariables) match {
        case null                             => FailedAssertion("Assertion result can't be null")
        case assertionResult: AssertionResult => assertionResult
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
    Context(ContextId.dummy, Map("contexts" -> resultsForNode))
  }

}
