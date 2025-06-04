package pl.touk.nussknacker.ui.api.description.scenarioLiveData

import io.circe._
import pl.touk.nussknacker.engine.api.deployment.LiveDataPreviewSupported.{
  ExceptionResult,
  InvocationResult,
  LiveData,
  LiveDataSample
}
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.ui.api.BaseHttpService.CustomAuthorizationError
import pl.touk.nussknacker.ui.processreport.NodeCount
import sttp.tapir.{Codec, CodecFormat, Schema}

import java.time.Instant

object Dtos {

  import sttp.tapir.json.circe._

  final case class LiveDataDto(
      timestamp: Instant,
      results: LiveDataResultsDto,
      counts: Map[String, NodeCount],
  )

  final case class LiveDataResultsDto(
      nodeTransitions: List[LiveDataForNodeTransitionDto],
      invocationResults: Map[String, List[InvocationResultDto]],
      externalInvocationResults: Map[String, List[InvocationResultDto]],
      exceptionsByNodeId: Map[String, List[ExceptionResultDto]],
  )

  object LiveDataDto {

    def from(liveData: LiveData, counts: Map[String, NodeCount]): LiveDataDto = {
      LiveDataDto(
        timestamp = liveData.timestamp,
        results = LiveDataResultsDto(
          nodeTransitions = liveData.nodeTransitions.map { case (nodeTransition, liveData) =>
            LiveDataForNodeTransitionDto(
              sourceNodeId = nodeTransition.sourceNodeId,
              destinationNodeId = nodeTransition.destinationNodeId,
              samples = liveData.samples.map(LiveDataSampleDto.from),
              totalCount = liveData.totalCount,
              currentThroughput = liveData.currentThroughput,
            )
          }.toList,
          invocationResults = liveData.invocationResults.map { case (nodeId, results) =>
            nodeId.id -> results.map(InvocationResultDto.from)
          },
          externalInvocationResults = liveData.externalInvocationResults.map { case (nodeId, results) =>
            nodeId.id -> results.map(InvocationResultDto.from)
          },
          exceptionsByNodeId = liveData.exceptions.map { case (nodeId, results) =>
            nodeId.id -> results.map(ExceptionResultDto.from)
          },
        ),
        counts = counts,
      )
    }

  }

  final case class ExceptionResultDto(
      contextId: String,
      timestamp: Instant,
      variables: Map[String, Json],
      errorMessage: Option[String],
  )

  object ExceptionResultDto {

    def from(exceptionResult: ExceptionResult): ExceptionResultDto =
      ExceptionResultDto(
        exceptionResult.contextId,
        exceptionResult.timestamp,
        exceptionResult.variables,
        Option(exceptionResult.throwable.getMessage),
      )

  }

  final case class InvocationResultDto(
      contextId: String,
      timestamp: Instant,
      name: String,
      value: Json,
  )

  object InvocationResultDto {

    def from(invocationResult: InvocationResult): InvocationResultDto =
      InvocationResultDto(
        invocationResult.contextId,
        invocationResult.timestamp,
        invocationResult.name,
        invocationResult.value,
      )

  }

  final case class LiveDataForNodeTransitionDto(
      sourceNodeId: String,
      destinationNodeId: Option[String],
      samples: List[LiveDataSampleDto],
      totalCount: Long,
      currentThroughput: BigDecimal,
  )

  case class LiveDataSampleDto(
      contextId: String,
      timestamp: Instant,
      variables: Map[String, Json],
  )

  object LiveDataSampleDto {
    def from(liveDataSample: LiveDataSample): LiveDataSampleDto =
      LiveDataSampleDto(liveDataSample.contextId, liveDataSample.timestamp, liveDataSample.variables)
  }

  implicit def exceptionResultDtoSchema: Schema[ExceptionResultDto]                     = Schema.derived
  implicit def invocationResultDtoSchema: Schema[InvocationResultDto]                   = Schema.derived
  implicit def liveDataSampleDtoSchema: Schema[LiveDataSampleDto]                       = Schema.derived
  implicit def liveDataForNodeTransitionDtoSchema: Schema[LiveDataForNodeTransitionDto] = Schema.derived
  implicit def nodeCountSchema: Schema[NodeCount]                                       = Schema.anyObject
  implicit def liveDataResultsDtoSchema: Schema[LiveDataResultsDto]                     = Schema.derived
  implicit def liveDataDtoSchema: Schema[LiveDataDto]                                   = Schema.derived

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
