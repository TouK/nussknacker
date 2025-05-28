package pl.touk.nussknacker.ui.api.description.scenarioLiveData

import io.circe._
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.testmode.TestProcess._
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.ui.api.BaseHttpService.CustomAuthorizationError
import pl.touk.nussknacker.ui.process.test.ResultsWithCounts
import pl.touk.nussknacker.ui.processreport.NodeCount
import sttp.tapir.{Codec, CodecFormat, Schema}

import scala.collection.compat._

object Dtos {

  import sttp.tapir.json.circe._
  lazy val typingResultEncoder: Encoder[TypingResult] = TypingResult.encoder

  final case class LiveDataDto(
      results: LiveDataSamplesDto,
      counts: Map[String, NodeCount],
      nodeTransitionThroughput: Option[List[NodeTransitionThroughputDto]],
  )

  object LiveDataDto {

    def from(
        resultsWithCounts: ResultsWithCounts,
        nodeTransitionThroughput: Option[Map[NodeTransition, BigDecimal]],
    ): LiveDataDto = {
      LiveDataDto(
        results = LiveDataSamplesDto.from(resultsWithCounts.results),
        counts = resultsWithCounts.counts,
        nodeTransitionThroughput = nodeTransitionThroughput.map(NodeTransitionThroughput.from),
      )
    }

  }

  final case class LiveDataSamplesDto(
      nodeTransitionResults: List[NodeTransitionResult],
      invocationResults: Map[String, List[ExpressionInvocationResult[Json]]],
      externalInvocationResults: Map[String, List[ExternalInvocationResult[Json]]],
      exceptions: List[ExceptionResult[Json]]
  )

  object LiveDataSamplesDto {

    def from(testResults: TestResults[Json]): LiveDataSamplesDto = {
      lazy val nodeTransitionResults = testResults.nodeTransitionResults.map { case (nodeTransition, results) =>
        NodeTransitionResult(
          sourceNodeId = nodeTransition.sourceNodeId,
          destinationNodeId = nodeTransition.destinationNodeId,
          results = results,
        )
      }.toList
      LiveDataSamplesDto(
        nodeTransitionResults = nodeTransitionResults,
        invocationResults = testResults.invocationResults,
        externalInvocationResults = testResults.externalInvocationResults,
        exceptions = testResults.exceptions,
      )
    }

  }

  final case class NodeTransitionResult(
      sourceNodeId: String,
      destinationNodeId: Option[String],
      results: List[ResultContext[Json]]
  )

  final case class NodeTransitionThroughputDto(
      sourceNodeId: String,
      destinationNodeId: Option[String],
      throughput: BigDecimal,
  )

  object NodeTransitionThroughput {

    def from(nodeTransitionThroughput: Map[NodeTransition, BigDecimal]): List[NodeTransitionThroughputDto] = {
      nodeTransitionThroughput.map { case (k, v) =>
        NodeTransitionThroughputDto(k.sourceNodeId, k.destinationNodeId, v)
      }.toList
    }

  }

  implicit def resultContextSchema: Schema[ResultContext[Json]]                           = Schema.derived
  implicit def expressionInvocationResultSchema: Schema[ExpressionInvocationResult[Json]] = Schema.derived
  implicit def externalInvocationResultSchema: Schema[ExternalInvocationResult[Json]]     = Schema.derived
  implicit def throwableSchema: Schema[Throwable]                                         = Schema.string
  implicit def exceptionResultSchema: Schema[ExceptionResult[Json]]                       = Schema.derived
  implicit def nodeTransitionResultSchema: Schema[NodeTransitionResult]                   = Schema.derived
  implicit def testResultsSchema: Schema[LiveDataSamplesDto]                              = Schema.derived
  implicit def nodeCountSchema: Schema[NodeCount]                                         = Schema.anyObject
  implicit def nodeTransitionThroughputDtoSchema: Schema[NodeTransitionThroughputDto]     = Schema.derived
  implicit def resultsWithCountsSchema: Schema[LiveDataDto]                               = Schema.derived
  implicit def typingResultDecoder: Decoder[TypingResult] = Decoder.decodeJson.map(_ => typing.Unknown)
  implicit def scenarioGraphSchema: Schema[ScenarioGraph] = Schema.anyObject

  sealed trait LiveDataError

  object LiveDataError {

    case object NoPermission extends LiveDataError with CustomAuthorizationError

    case object NoScenario extends LiveDataError

    implicit val noScenarioErrorCodec: Codec[String, NoScenario.type, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[NoScenario.type] { _ =>
        s"Scenario not found"
      }

    case object LiveDataNotSupported extends LiveDataError

    implicit val liveDataNotSupportedErrorCodec: Codec[String, LiveDataNotSupported.type, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[LiveDataNotSupported.type] { _ =>
        s"Live data preview is not supported by this scenario"
      }

    case object LiveDataNotAvailable extends LiveDataError

    implicit val liveDataNotAvailableErrorCodec: Codec[String, LiveDataNotAvailable.type, CodecFormat.TextPlain] =
      BaseEndpointDefinitions.toTextPlainCodecSerializationOnly[LiveDataNotAvailable.type] { _ =>
        s"There is currently no live data available for this scenario"
      }

  }

}
