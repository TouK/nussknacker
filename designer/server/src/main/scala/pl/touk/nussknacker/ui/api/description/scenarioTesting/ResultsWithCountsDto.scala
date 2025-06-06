package pl.touk.nussknacker.ui.api.description.scenarioTesting

import io.circe._
import pl.touk.nussknacker.engine.livedata.CollectedLiveData
import pl.touk.nussknacker.engine.testmode.TestProcess._
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Test.{SkipResultsPerNode, SkipResultsPerTransition}
import pl.touk.nussknacker.ui.process.test.ResultsWithCounts
import pl.touk.nussknacker.ui.processreport.NodeCount
import sttp.tapir.Schema

import java.time.Instant
import scala.collection.compat._

final case class ResultsWithCountsDto(timestamp: Instant, results: TestResultsDto, counts: Map[String, NodeCount])

object ResultsWithCountsDto {

  def from(
      resultsWithCounts: ResultsWithCounts,
      skipResultsPerNode: SkipResultsPerNode,
      skipResultsPerTransition: SkipResultsPerTransition
  ): ResultsWithCountsDto = {
    lazy val nodeTransitionResults = resultsWithCounts.results.nodeTransitionResults.map {
      case (nodeTransition, results) =>
        NodeTransitionResult(
          sourceNodeId = nodeTransition.sourceNodeId,
          destinationNodeId = nodeTransition.destinationNodeId,
          results = results,
          // In test results, the totalCount and currentThrougput are not available (only available for live data)
          totalCount = None,
          currentThroughput = None,
        )
    }.toList
    lazy val exceptionsByNodeId = resultsWithCounts.results.exceptions.groupBy(_.nodeId).collect {
      case (Some(nodeId), exceptions) => (nodeId, exceptions)
    }
    ResultsWithCountsDto(
      timestamp = resultsWithCounts.timestamp,
      results = TestResultsDto(
        nodeResults = Option.when(!skipResultsPerNode.value)(resultsWithCounts.results.nodeResults),
        nodeTransitionResults = Option.when(!skipResultsPerTransition.value)(nodeTransitionResults),
        invocationResults = resultsWithCounts.results.invocationResults,
        externalInvocationResults = resultsWithCounts.results.externalInvocationResults,
        exceptions = resultsWithCounts.results.exceptions,
        exceptionsByNodeId = exceptionsByNodeId,
      ),
      counts = resultsWithCounts.counts,
    )
  }

  def from(liveData: CollectedLiveData, counts: Map[String, NodeCount]): ResultsWithCountsDto = {
    lazy val exceptionsByNodeId = liveData.exceptions.map { case (nodeId, results) =>
      nodeId.id -> results.map(e =>
        ExceptionResult(ResultContext(e.contextId, e.timestamp, e.variables), Some(nodeId.id), e.throwable)
      )
    }
    ResultsWithCountsDto(
      timestamp = liveData.timestamp,
      results = TestResultsDto(
        nodeResults = None,
        nodeTransitionResults = Some(
          liveData.nodeTransitions.map { case (nodeTransition, liveData) =>
            NodeTransitionResult(
              sourceNodeId = nodeTransition.sourceNodeId,
              destinationNodeId = nodeTransition.destinationNodeId,
              results = liveData.samples.map(s => ResultContext(s.contextId, s.timestamp, s.variables)),
              totalCount = Some(liveData.totalCount),
              currentThroughput = Some(liveData.currentThroughput),
            )
          }.toList
        ),
        invocationResults = liveData.invocationResults.map { case (nodeId, results) =>
          nodeId.id -> results.map(r => ExpressionInvocationResult(r.contextId, r.timestamp, r.name, r.value))
        },
        externalInvocationResults = liveData.externalInvocationResults.map { case (nodeId, results) =>
          nodeId.id -> results.map(r => ExternalInvocationResult(r.contextId, r.timestamp, r.name, r.value))
        },
        exceptions = exceptionsByNodeId.values.toList.flatten,
        exceptionsByNodeId = exceptionsByNodeId,
      ),
      counts = counts,
    )
  }

  import sttp.tapir.json.circe._

  implicit def resultContextSchema: Schema[ResultContext[Json]]                           = Schema.derived
  implicit def expressionInvocationResultSchema: Schema[ExpressionInvocationResult[Json]] = Schema.derived
  implicit def externalInvocationResultSchema: Schema[ExternalInvocationResult[Json]]     = Schema.derived
  implicit def throwableSchema: Schema[Throwable]                                         = Schema.string
  implicit def exceptionResultSchema: Schema[ExceptionResult[Json]]                       = Schema.derived
  implicit def nodeTransitionResultSchema: Schema[NodeTransitionResult]                   = Schema.derived
  implicit def testResultsSchema: Schema[TestResultsDto]                                  = Schema.derived
  implicit def nodeCountSchema: Schema[NodeCount]                                         = Schema.anyObject
  implicit def resultsWithCountsSchema: Schema[ResultsWithCountsDto]                      = Schema.derived

}

final case class TestResultsDto(
    nodeResults: Option[Map[String, List[ResultContext[Json]]]],
    nodeTransitionResults: Option[List[NodeTransitionResult]],
    invocationResults: Map[String, List[ExpressionInvocationResult[Json]]],
    externalInvocationResults: Map[String, List[ExternalInvocationResult[Json]]],
    exceptions: List[ExceptionResult[Json]],
    exceptionsByNodeId: Map[String, List[ExceptionResult[Json]]],
)

final case class NodeTransitionResult(
    sourceNodeId: String,
    destinationNodeId: Option[String],
    results: List[ResultContext[Json]],
    totalCount: Option[Long],
    currentThroughput: Option[BigDecimal],
)
