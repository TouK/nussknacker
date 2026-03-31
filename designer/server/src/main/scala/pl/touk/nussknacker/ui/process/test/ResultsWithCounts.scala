package pl.touk.nussknacker.ui.process.test

import io.circe.Json
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.testmode.TestProcess._
import pl.touk.nussknacker.ui.process.test.JsonTestResults.JsonExceptionResult
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
    exceptions: List[JsonExceptionResult],
)

object JsonTestResults {

  final case class JsonExceptionResult(
      context: ResultContext[Json],
      nodeId: Option[NodeId],
      message: Option[String],
  )

  object JsonExceptionResult {

    def apply(exceptionResult: ExceptionResult[Json]): JsonExceptionResult = JsonExceptionResult(
      context = exceptionResult.context,
      nodeId = exceptionResult.nodeId,
      message = Option(exceptionResult.throwable.getMessage),
    )

  }

  def from(testResults: TestResults[Json]): JsonTestResults = JsonTestResults(
    nodeResults = testResults.nodeResults,
    nodeTransitionResults = testResults.nodeTransitionResults,
    expressionEvaluationResults = testResults.expressionEvaluationResults,
    externalServiceInvocationResults = testResults.externalServiceInvocationResults,
    exceptions = testResults.exceptions.map(JsonExceptionResult(_)),
  )

}
