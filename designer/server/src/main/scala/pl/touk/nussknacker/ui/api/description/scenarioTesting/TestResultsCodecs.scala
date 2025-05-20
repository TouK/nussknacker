package pl.touk.nussknacker.ui.api.description.scenarioTesting

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import pl.touk.nussknacker.engine.api.ContextId
import pl.touk.nussknacker.engine.testmode.TestProcess.{
  ExceptionResult,
  ExpressionInvocationResult,
  ExternalInvocationResult,
  ResultContext
}
import pl.touk.nussknacker.ui.api.description.scenarioTesting.Dtos.{
  NodeTransitionFrequencyDto,
  NodeTransitionResult,
  ResultsWithCountsDto,
  TestResultsDto
}

object TestResultsCodecs {

  import io.circe.syntax._
  import pl.touk.nussknacker.engine.api.CirceUtil._

  implicit val resultsWithCountsEncoder: Encoder[ResultsWithCountsDto] =
    deriveConfiguredEncoder[ResultsWithCountsDto].mapJson(_.dropNullValues)

  implicit val resultsWithCountsDecoder: Decoder[ResultsWithCountsDto] =
    Decoder.failed(DecodingFailure("Not implemented", List.empty))

  implicit val testResultsEncoder: Encoder[TestResultsDto] = new Encoder[TestResultsDto]() {

    implicit val contextId: Encoder[ContextId]            = Encoder.encodeString.contramap(_.serialize)
    implicit val nodeResult: Encoder[ResultContext[Json]] = deriveConfiguredEncoder
    implicit val expressionInvocationResult: Encoder[ExpressionInvocationResult[Json]] = deriveConfiguredEncoder
    implicit val externalInvocationResult: Encoder[ExternalInvocationResult[Json]]     = deriveConfiguredEncoder
    implicit val nodeTransitionResult: Encoder[NodeTransitionResult]                   = deriveConfiguredEncoder

    // TODO: do we want more information here?
    implicit val throwableEncoder: Encoder[Throwable] = Encoder[Option[String]].contramap(th => Option(th.getMessage))
    implicit val exceptionResultEncoder: Encoder[ExceptionResult[Json]] = deriveConfiguredEncoder

    override def apply(a: TestResultsDto): Json = a match {
      case TestResultsDto(
            nodeResults,
            nodeTransitionResults,
            invocationResults,
            externalInvocationResults,
            exceptions
          ) =>
        Json.obj(
          "nodeResults" -> nodeResults
            .map(_.map { case (node, list) => node -> list.sortBy(_.id.serialize) }.asJson)
            .getOrElse(Json.Null),
          "nodeTransitionResults" -> nodeTransitionResults.asJson,
          "invocationResults" -> invocationResults.map { case (node, list) =>
            node -> list.sortBy(_.contextId.serialize)
          }.asJson,
          "externalInvocationResults" -> externalInvocationResults.map { case (node, list) =>
            node -> list.sortBy(_.contextId.serialize)
          }.asJson,
          "exceptions" -> exceptions.sortBy(_.context.id.serialize).asJson
        )
    }

  }

  implicit val nodeTransitionFrequencyDto: Encoder[NodeTransitionFrequencyDto] = deriveConfiguredEncoder

  implicit val testResultsDecoder: Decoder[TestResultsDto] =
    Decoder.failed(DecodingFailure("Not implemented", List.empty))

}
