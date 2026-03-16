package pl.touk.nussknacker.ui.api.description.scenarioTesting

import io.circe._
import pl.touk.nussknacker.engine.api.{ContextId, NodeId}
import pl.touk.nussknacker.engine.livedata.CollectedLiveData
import pl.touk.nussknacker.engine.testmode.TestProcess._
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Test.{SkipResultsPerNode, SkipResultsPerTransition}
import pl.touk.nussknacker.ui.api.description.scenarioTesting.ResultsWithCountsDtoCodecs.{
  ContextIdDto,
  ContextIdPathPartDto
}
import pl.touk.nussknacker.ui.process.test.ResultsWithCounts
import pl.touk.nussknacker.ui.process.test.testcase.AssertionResult
import pl.touk.nussknacker.ui.processreport.NodeCount
import sttp.tapir.Schema

import java.time.Instant
import scala.collection.compat._

final case class ResultsWithCountsDto(
    timestamp: Instant,
    results: TestResultsDto,
    counts: Map[NodeId, NodeCount],
    assertionsResults: Map[NodeId, List[AssertionResult]]
)

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
        expressionEvaluationResults = resultsWithCounts.results.expressionEvaluationResults,
        externalServiceInvocationResults = resultsWithCounts.results.externalServiceInvocationResults,
        exceptions = resultsWithCounts.results.exceptions,
        exceptionsByNodeId = exceptionsByNodeId,
      ),
      counts = resultsWithCounts.counts,
      assertionsResults = resultsWithCounts.assertionsResults
    )
  }

  def from(liveData: CollectedLiveData, counts: Map[NodeId, NodeCount]): ResultsWithCountsDto = {
    lazy val exceptionsByNodeId = liveData.exceptions.map { case (nodeId, results) =>
      nodeId -> results.map(e =>
        ExceptionResult(ResultContext(e.contextId, e.timestamp, e.variables), Some(nodeId), e.throwable)
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
        expressionEvaluationResults = liveData.expressionEvaluationResults.map { case (nodeId, results) =>
          nodeId -> results.map(r => ExpressionEvaluationResult(r.contextId, r.timestamp, r.name, r.value))
        },
        externalServiceInvocationResults = liveData.externalServiceInvocationResults.map { case (nodeId, results) =>
          nodeId -> results.map(r => ExternalServiceInvocationResult(r.contextId, r.timestamp, r.name, r.value))
        },
        exceptions = exceptionsByNodeId.values.toList.flatten,
        exceptionsByNodeId = exceptionsByNodeId,
      ),
      counts = counts,
      assertionsResults = Map.empty
    )
  }

  import sttp.tapir.json.circe._

  implicit def nodeIdSchema: Schema[NodeId]                             = Schema.derived
  implicit def nodeIdKeyMapSchema[V: Schema]: Schema[Map[NodeId, V]]    = Schema.schemaForMap[NodeId, V](_.value)
  implicit def contextIdPathPartDtoSchema: Schema[ContextIdPathPartDto] = Schema.derived
  implicit def contextIdSchema: Schema[ContextId] =
    Schema.derived[ContextIdDto].map(_ => None)(ContextIdDto.from)
  implicit def resultContextSchema: Schema[ResultContext[Json]]                              = Schema.derived
  implicit def expressionInvocationResultSchema: Schema[ExpressionEvaluationResult[Json]]    = Schema.derived
  implicit def externalInvocationResultSchema: Schema[ExternalServiceInvocationResult[Json]] = Schema.derived
  implicit def throwableSchema: Schema[Throwable]                                            = Schema.string
  implicit def exceptionResultSchema: Schema[ExceptionResult[Json]]                          = Schema.derived
  implicit def nodeTransitionResultSchema: Schema[NodeTransitionResult]                      = Schema.derived
  implicit def testResultsSchema: Schema[TestResultsDto]                                     = Schema.derived
  implicit def nodeCountSchema: Schema[NodeCount]                                            = Schema.anyObject
  implicit def assertionResultsSchema: Schema[AssertionResult]                               = Schema.derived
  implicit def resultsWithCountsSchema: Schema[ResultsWithCountsDto]                         = Schema.derived

}

final case class TestResultsDto(
    nodeResults: Option[Map[NodeId, List[ResultContext[Json]]]],
    nodeTransitionResults: Option[List[NodeTransitionResult]],
    expressionEvaluationResults: Map[NodeId, List[ExpressionEvaluationResult[Json]]],
    externalServiceInvocationResults: Map[NodeId, List[ExternalServiceInvocationResult[Json]]],
    exceptions: List[ExceptionResult[Json]],
    exceptionsByNodeId: Map[NodeId, List[ExceptionResult[Json]]],
)

final case class NodeTransitionResult(
    sourceNodeId: NodeId,
    destinationNodeId: Option[NodeId],
    results: List[ResultContext[Json]],
    totalCount: Option[Long],
    currentThroughput: Option[BigDecimal],
)
