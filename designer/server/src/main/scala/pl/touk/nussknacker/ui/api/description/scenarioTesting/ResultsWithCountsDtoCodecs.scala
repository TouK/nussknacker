package pl.touk.nussknacker.ui.api.description.scenarioTesting

import io.circe.{Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import pl.touk.nussknacker.engine.testmode.TestProcess.{
  ExceptionResult,
  ExpressionInvocationResult,
  ExternalInvocationResult,
  ResultContext
}

object ResultsWithCountsDtoCodecs {

  import io.circe.syntax._
  import pl.touk.nussknacker.engine.api.CirceUtil._

  implicit val resultsWithCountsEncoder: Encoder[ResultsWithCountsDto] =
    deriveConfiguredEncoder

  implicit val resultsWithCountsDecoder: Decoder[ResultsWithCountsDto] =
    Decoder.failed(DecodingFailure("Not implemented", List.empty))

  private implicit val testResultsEncoder: Encoder[TestResultsDto] = new Encoder[TestResultsDto]() {

    implicit val nodeResult: Encoder[ResultContext[Json]]                              = deriveConfiguredEncoder
    implicit val expressionInvocationResult: Encoder[ExpressionInvocationResult[Json]] = deriveConfiguredEncoder
    implicit val externalInvocationResult: Encoder[ExternalInvocationResult[Json]]     = deriveConfiguredEncoder
    implicit val nodeTransitionResultEncoder: Encoder[NodeTransitionResult] = Encoder.instance { value =>
      val baseFields: List[(String, Option[Json])] = List(
        "sourceNodeId"      -> Some(Json.fromString(value.sourceNodeId)),
        "destinationNodeId" -> Some(value.destinationNodeId.asJson), // Always include json field (even when None)
        "results"           -> Some(value.results.asJson),
        "totalCount"        -> value.totalCount.map(Json.fromLong),  // Drop json field when None
        "currentThroughput" -> value.currentThroughput.map(_.asJson) // Drop json field when None
      )
      Json.fromJsonObject(
        JsonObject.fromIterable(
          baseFields.collect { case (key, Some(json)) =>
            key -> json
          }
        )
      )
    }

    // TODO: do we want more information here?
    implicit val throwableEncoder: Encoder[Throwable] = Encoder[Option[String]].contramap(th => Option(th.getMessage))
    implicit val exceptionResultEncoder: Encoder[ExceptionResult[Json]] = deriveConfiguredEncoder

    override def apply(a: TestResultsDto): Json = a match {
      case TestResultsDto(
            nodeResults,
            nodeTransitionResults,
            invocationResults,
            externalInvocationResults,
            exceptions,
            exceptionsByNodeId,
          ) =>
        Json.obj(
          "nodeResults" -> nodeResults
            .map(_.map { case (node, list) => node -> list.sortBy(_.id) }.asJson)
            .getOrElse(Json.Null),
          "nodeTransitionResults" -> nodeTransitionResults.asJson.deepDropNullValues,
          "invocationResults" -> invocationResults.map { case (node, list) => node -> list.sortBy(_.contextId) }.asJson,
          "externalInvocationResults" -> externalInvocationResults.map { case (node, list) =>
            node -> list.sortBy(_.contextId)
          }.asJson,
          "exceptions" -> exceptions.sortBy(_.context.id).asJson,
          "exceptionsByNodeId" -> exceptionsByNodeId.map { case (nodeId, exs) =>
            nodeId -> exs.sortBy(_.context.id)
          }.asJson,
        )
    }

  }

}
