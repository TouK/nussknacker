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

/** A projection of [[pl.touk.nussknacker.engine.testmode.TestProcess.TestResults]] that contains only JSON-typed,
  * serialization-safe data that can be handled by the carrotsearch RamUsageEstimator used for size estimation.
  * Compared to [[pl.touk.nussknacker.engine.testmode.TestProcess.TestResults]], it drops `originalNodeResults`
  * (raw objects used only internally for assertion verification) and replaces throwable in
  * exceptions with message string only, as both may contain hidden-class lambdas that RamUsageEstimator cannot handle.
  */
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
      throwable: Option[String],
  )

  object JsonExceptionResult {

    def apply(exceptionResult: ExceptionResult[Json]): JsonExceptionResult = JsonExceptionResult(
      context = exceptionResult.context,
      nodeId = exceptionResult.nodeId,
      throwable = Option(exceptionResult.throwable.getMessage),
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
