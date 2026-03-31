package pl.touk.nussknacker.ui.process.test

import io.circe.Json
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.testmode.TestProcess._
import pl.touk.nussknacker.ui.process.test.testcase.AssertionResult
import pl.touk.nussknacker.ui.processreport.NodeCount

import java.time.Instant

final case class ResultsWithCounts(
    timestamp: Instant,
    results: JsonTestResults,
    counts: Map[NodeId, NodeCount],
    assertionsResults: Map[NodeId, List[AssertionResult]] = Map.empty
)

final case class JsonTestResults(
    nodeResults: Map[NodeId, List[ResultContext[Json]]],
    nodeTransitionResults: Map[NodeTransition, List[ResultContext[Json]]],
    expressionEvaluationResults: Map[NodeId, List[ExpressionEvaluationResult[Json]]],
    externalServiceInvocationResults: Map[NodeId, List[ExternalServiceInvocationResult[Json]]],
    exceptions: List[ExceptionResult[Json]],
)

object JsonTestResults {

  def from(testResults: TestResults[Json]): JsonTestResults = JsonTestResults(
    nodeResults = testResults.nodeResults,
    nodeTransitionResults = testResults.nodeTransitionResults,
    expressionEvaluationResults = testResults.expressionEvaluationResults,
    externalServiceInvocationResults = testResults.externalServiceInvocationResults,
    exceptions = testResults.exceptions,
  )

}
