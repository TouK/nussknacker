package pl.touk.nussknacker.ui.api.description.scenarioLiveData

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import pl.touk.nussknacker.engine.testmode.TestProcess.{
  ExceptionResult,
  ExpressionInvocationResult,
  ExternalInvocationResult,
  ResultContext
}
import pl.touk.nussknacker.ui.api.description.scenarioLiveData.Dtos.{
  LiveDataDto,
  LiveDataSamplesDto,
  NodeTransitionResult,
  NodeTransitionThroughputDto
}

object LiveDataCodecs {

  import io.circe.syntax._
  import pl.touk.nussknacker.engine.api.CirceUtil._

  implicit val resultsWithCountsEncoder: Encoder[LiveDataDto] =
    deriveConfiguredEncoder[LiveDataDto].mapJson(_.dropNullValues)

  implicit val resultsWithCountsDecoder: Decoder[LiveDataDto] =
    Decoder.failed(DecodingFailure("Not implemented", List.empty))

  implicit val testResultsEncoder: Encoder[LiveDataSamplesDto] = new Encoder[LiveDataSamplesDto]() {

    implicit val nodeResult: Encoder[ResultContext[Json]]                              = deriveConfiguredEncoder
    implicit val expressionInvocationResult: Encoder[ExpressionInvocationResult[Json]] = deriveConfiguredEncoder
    implicit val externalInvocationResult: Encoder[ExternalInvocationResult[Json]]     = deriveConfiguredEncoder
    implicit val nodeTransitionResult: Encoder[NodeTransitionResult]                   = deriveConfiguredEncoder
    implicit val exceptionResultEncoder: Encoder[ExceptionResult[Json]]                = deriveConfiguredEncoder
    implicit val throwableEncoder: Encoder[Throwable] = Encoder[Option[String]].contramap(th => Option(th.getMessage))

    override def apply(a: LiveDataSamplesDto): Json = a match {
      case LiveDataSamplesDto(
            nodeTransitionResults,
            invocationResults,
            externalInvocationResults,
            exceptions
          ) =>
        Json.obj(
          "nodeTransitionResults" -> nodeTransitionResults.asJson,
          "invocationResults" -> invocationResults.map { case (node, list) => node -> list.sortBy(_.contextId) }.asJson,
          "externalInvocationResults" -> externalInvocationResults.map { case (node, list) =>
            node -> list.sortBy(_.contextId)
          }.asJson,
          "exceptions" -> exceptions.sortBy(_.context.id).asJson
        )
    }

  }

  implicit val nodeTransitionThroughputDto: Encoder[NodeTransitionThroughputDto] = deriveConfiguredEncoder

  implicit val testResultsDecoder: Decoder[LiveDataSamplesDto] =
    Decoder.failed(DecodingFailure("Not implemented", List.empty))

}
