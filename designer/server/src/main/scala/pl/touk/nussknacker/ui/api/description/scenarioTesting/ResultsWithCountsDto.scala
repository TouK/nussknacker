package pl.touk.nussknacker.ui.api.description.scenarioTesting

import io.circe._
import pl.touk.nussknacker.engine.api.{ContextId, NodeId}
import pl.touk.nussknacker.engine.api.context.ContextTransformation
import pl.touk.nussknacker.engine.livedata.CollectedLiveData
import pl.touk.nussknacker.engine.testmode.TestProcess._
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.Test.{SkipResultsPerNode, SkipResultsPerTransition}
import pl.touk.nussknacker.ui.api.description.scenarioTesting.ResultsWithCountsDtoCodecs.{
  ContextIdDto,
  ContextIdPathPartDto
}
import pl.touk.nussknacker.ui.process.test.JsonTestResults.JsonExceptionResult
import pl.touk.nussknacker.ui.process.test.ResultsWithCounts
import pl.touk.nussknacker.ui.process.test.testcase.AssertionResult
import pl.touk.nussknacker.ui.processreport.NodeCount
import sttp.tapir.Schema

import java.time.Instant
import scala.collection.mutable

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
      skipResultsPerTransition: SkipResultsPerTransition,
      nodeNamesById: Map[NodeId, String],
  ): ResultsWithCountsDto = {
    lazy val externalServiceInvocationResults = resultsWithCounts.results.externalServiceInvocationResults
    lazy val rawNodeTransitionResults = resultsWithCounts.results.nodeTransitionResults.map {
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
    lazy val nodeTransitionResults =
      withSinkOutputTransitions(rawNodeTransitionResults, externalServiceInvocationResults)
    lazy val exceptionsByNodeId = resultsWithCounts.results.exceptions.groupBy(_.nodeId).collect {
      case (Some(nodeId), exceptions) => (nodeId, exceptions)
    }
    ResultsWithCountsDto(
      timestamp = resultsWithCounts.timestamp,
      results = TestResultsDto(
        nodeResults = Option.when(!skipResultsPerNode.value)(resultsWithCounts.results.nodeResults),
        nodeTransitionResults = Option.when(!skipResultsPerTransition.value)(nodeTransitionResults),
        expressionEvaluationResults = resultsWithCounts.results.expressionEvaluationResults,
        externalServiceInvocationResults = externalServiceInvocationResults,
        nodeNamesByContextKey = buildNodeNamesByContextKey(nodeNamesById),
        exceptions = resultsWithCounts.results.exceptions,
        exceptionsByNodeId = exceptionsByNodeId,
      ),
      counts = resultsWithCounts.counts,
      assertionsResults = resultsWithCounts.assertionsResults
    )
  }

  def from(
      liveData: CollectedLiveData,
      counts: Map[NodeId, NodeCount],
      nodeNamesById: Map[NodeId, String],
  ): ResultsWithCountsDto = {
    lazy val externalServiceInvocationResults = liveData.externalServiceInvocationResults.map {
      case (nodeId, results) =>
        nodeId -> results.map(r => ExternalServiceInvocationResult(r.contextId, r.timestamp, r.name, r.value))
    }
    lazy val rawNodeTransitionResults = liveData.nodeTransitions.map { case (nodeTransition, liveData) =>
      NodeTransitionResult(
        sourceNodeId = nodeTransition.sourceNodeId,
        destinationNodeId = nodeTransition.destinationNodeId,
        results = liveData.samples.map(s => ResultContext(s.contextId, s.timestamp, s.variables)),
        totalCount = Some(liveData.totalCount),
        currentThroughput = Some(liveData.currentThroughput),
      )
    }.toList
    lazy val nodeTransitionResults =
      withSinkOutputTransitions(rawNodeTransitionResults, externalServiceInvocationResults)
    lazy val exceptionsByNodeId = liveData.exceptions.map { case (nodeId, results) =>
      nodeId -> results.map(e =>
        JsonExceptionResult(
          ResultContext(e.contextId, e.timestamp, e.variables),
          Some(nodeId),
          Option(e.throwable.getMessage)
        )
      )
    }
    ResultsWithCountsDto(
      timestamp = liveData.timestamp,
      results = TestResultsDto(
        nodeResults = None,
        nodeTransitionResults = Some(nodeTransitionResults),
        expressionEvaluationResults = liveData.expressionEvaluationResults.map { case (nodeId, results) =>
          nodeId -> results.map(r => ExpressionEvaluationResult(r.contextId, r.timestamp, r.name, r.value))
        },
        externalServiceInvocationResults = externalServiceInvocationResults,
        nodeNamesByContextKey = buildNodeNamesByContextKey(nodeNamesById),
        exceptions = exceptionsByNodeId.values.toList.flatten,
        exceptionsByNodeId = exceptionsByNodeId,
      ),
      counts = counts,
      assertionsResults = Map.empty
    )
  }

  private def withSinkOutputTransitions(
      nodeTransitionResults: List[NodeTransitionResult],
      externalServiceInvocationResults: Map[NodeId, List[ExternalServiceInvocationResult[Json]]],
  ): List[NodeTransitionResult] = {
    val sourceNodesWithTransitions = nodeTransitionResults.iterator.map(_.sourceNodeId).toSet
    val inputResultsByDestinationAndContextId = nodeTransitionResults.iterator.flatMap { transition =>
      transition.destinationNodeId.iterator.flatMap { destinationNodeId =>
        transition.results.iterator.map(result => (destinationNodeId, result.id) -> result)
      }
    }.toMap

    val sinkOutputTransitions = externalServiceInvocationResults.iterator
      .filter { case (nodeId, results) =>
        results.nonEmpty && !sourceNodesWithTransitions.contains(nodeId)
      }
      .toList
      .sortBy { case (nodeId, _) => nodeId.value }
      .map { case (nodeId, invocations) =>
        createSinkOutputTransition(nodeId, invocations, inputResultsByDestinationAndContextId)
      }

    nodeTransitionResults ++ sinkOutputTransitions
  }

  private def createSinkOutputTransition(
      nodeId: NodeId,
      invocations: List[ExternalServiceInvocationResult[Json]],
      inputResultsByDestinationAndContextId: Map[(NodeId, ContextId), ResultContext[Json]],
  ): NodeTransitionResult = {
    val invocationsByContextId = mutable.LinkedHashMap.empty[ContextId, List[ExternalServiceInvocationResult[Json]]]
    invocations.sortBy(_.timestamp).foreach { invocation =>
      invocationsByContextId.updateWith(invocation.contextId) {
        case Some(existing) => Some(existing :+ invocation)
        case None           => Some(List(invocation))
      }
    }

    val outputResults = invocationsByContextId.map { case (contextId, invocationsForContext) =>
      val firstInvocation = invocationsForContext.headOption
      val inputResult     = inputResultsByDestinationAndContextId.get((nodeId, contextId))
      ResultContext(
        id = contextId,
        timestamp = firstInvocation.map(_.timestamp).orElse(inputResult.map(_.timestamp)).getOrElse(Instant.EPOCH),
        variables =
          invocationsForContext.zipWithIndex.foldLeft(Map.empty[String, Json]) { case (acc, (invocation, index)) =>
            val fallbackName = s"Service invocation ${index + 1}"
            val name         = Option(invocation.name).filter(_.nonEmpty).getOrElse(fallbackName)
            acc.updated(name, invocation.value)
          },
      )
    }.toList

    NodeTransitionResult(
      sourceNodeId = nodeId,
      destinationNodeId = None,
      results = outputResults,
      totalCount = Some(outputResults.size.toLong),
      currentThroughput = None,
    )
  }

  private def buildNodeNamesByContextKey(nodeNamesById: Map[NodeId, String]): Map[String, String] = {
    val namesByRawNodeId = nodeNamesById.map { case (nodeId, nodeName) =>
      nodeId.value -> nodeName
    }

    val namesBySanitizedNodeId = nodeNamesById
      .groupBy { case (nodeId, _) =>
        ContextTransformation.sanitizeBranchName(nodeId.value)
      }
      .collect {
        case (sanitizedNodeId, entries) if entries.size == 1 =>
          sanitizedNodeId -> entries.head._2
      }

    namesByRawNodeId ++ namesBySanitizedNodeId
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
  implicit def exceptionResultSchema: Schema[JsonExceptionResult]                            = Schema.derived
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
    nodeNamesByContextKey: Map[String, String] = Map.empty,
    exceptions: List[JsonExceptionResult],
    exceptionsByNodeId: Map[NodeId, List[JsonExceptionResult]],
)

final case class NodeTransitionResult(
    sourceNodeId: NodeId,
    destinationNodeId: Option[NodeId],
    results: List[ResultContext[Json]],
    totalCount: Option[Long],
    currentThroughput: Option[BigDecimal],
)
