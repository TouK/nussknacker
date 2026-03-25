package pl.touk.nussknacker.ui.api.description.scenarioTesting

import io.circe.Json
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{ContextId, NodeId}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.testmode.TestProcess.{
  ExternalServiceInvocationResult,
  NodeTransition,
  ResultContext,
  TestResults
}
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Test.{SkipResultsPerNode, SkipResultsPerTransition}
import pl.touk.nussknacker.ui.process.test.ResultsWithCounts

import java.time.Instant

class ResultsWithCountsDtoSpec extends AnyFunSuite with Matchers with OptionValues {

  test("should add sink output transition with destinationNodeId = null when external invocations are present") {
    val sinkNodeId     = NodeId("sink")
    val processorNode  = NodeId("processor")
    val contextId      = ContextId(ProcessName("scenario"), NodeId("source"), taskId = 1, index = 0)
    val inputTimestamp = Instant.parse("2026-03-25T11:00:00Z")
    val sinkTimestamp  = Instant.parse("2026-03-25T11:01:00Z")
    val inputResult    = ResultContext(contextId, inputTimestamp, Map("input" -> Json.fromString("input-value")))
    val sinkInvocation =
      ExternalServiceInvocationResult(contextId, sinkTimestamp, "Kafka", Json.fromString("sink-value"))

    val testResults = TestResults[Json](
      nodeResults = Map.empty,
      nodeTransitionResults = Map(
        NodeTransition(processorNode, Some(sinkNodeId)) -> List(inputResult),
      ),
      expressionEvaluationResults = Map.empty,
      externalServiceInvocationResults = Map(
        sinkNodeId -> List(sinkInvocation),
      ),
      exceptions = List.empty,
      originalNodeResults = Map.empty,
    )

    val dto = ResultsWithCountsDto.from(
      resultsWithCounts = ResultsWithCounts(Instant.parse("2026-03-25T12:00:00Z"), testResults, counts = Map.empty),
      skipResultsPerNode = SkipResultsPerNode(value = false),
      skipResultsPerTransition = SkipResultsPerTransition(value = false),
    )

    val sinkTransition = dto.results.nodeTransitionResults.value.find(result =>
      result.sourceNodeId == sinkNodeId && result.destinationNodeId.isEmpty
    )

    sinkTransition.value.results should have size 1
    sinkTransition.value.results.head.id shouldBe contextId
    sinkTransition.value.results.head.timestamp shouldBe sinkTimestamp
    sinkTransition.value.results.head.variables shouldBe Map("Kafka" -> Json.fromString("sink-value"))
    sinkTransition.value.totalCount shouldBe Some(1L)
    sinkTransition.value.currentThroughput shouldBe None
  }

  test("should not add synthetic null-destination transition when node already has outgoing transition") {
    val processorNode = NodeId("processor")
    val sinkNode      = NodeId("sink")
    val contextId     = ContextId(ProcessName("scenario"), NodeId("source"), taskId = 1, index = 0)
    val outputResult  = ResultContext(contextId, Instant.parse("2026-03-25T11:00:00Z"), Map("a" -> Json.fromInt(1)))
    val serviceCall =
      ExternalServiceInvocationResult(contextId, Instant.parse("2026-03-25T11:01:00Z"), "http", Json.True)
    val testResults = TestResults[Json](
      nodeResults = Map.empty,
      nodeTransitionResults = Map(
        NodeTransition(processorNode, Some(sinkNode)) -> List(outputResult),
      ),
      expressionEvaluationResults = Map.empty,
      externalServiceInvocationResults = Map(processorNode -> List(serviceCall)),
      exceptions = List.empty,
      originalNodeResults = Map.empty,
    )

    val dto = ResultsWithCountsDto.from(
      resultsWithCounts = ResultsWithCounts(Instant.parse("2026-03-25T12:00:00Z"), testResults, counts = Map.empty),
      skipResultsPerNode = SkipResultsPerNode(value = false),
      skipResultsPerTransition = SkipResultsPerTransition(value = false),
    )

    val transitionsFromProcessor = dto.results.nodeTransitionResults.value.filter(_.sourceNodeId == processorNode)
    transitionsFromProcessor should have size 1
    transitionsFromProcessor.head.destinationNodeId shouldBe Some(sinkNode)
  }

}
