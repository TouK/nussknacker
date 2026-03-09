package pl.touk.nussknacker.ui.api.description.scenarioTesting

import io.circe.{Decoder, DecodingFailure, Encoder, Json, JsonObject}
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import io.circe.generic.semiauto.deriveEncoder
import pl.touk.nussknacker.engine.api.{ContextId, NodeId}
import pl.touk.nussknacker.engine.testmode.TestProcess.{
  ExceptionResult,
  ExpressionEvaluationResult,
  ExternalServiceInvocationResult,
  ResultContext
}
import pl.touk.nussknacker.ui.process.test.testcase.AssertionResult

object ResultsWithCountsDtoCodecs {

  import io.circe.syntax._
  import pl.touk.nussknacker.engine.api.CirceUtil._

  implicit val assertionResultEncoder: Encoder[AssertionResult] =
    deriveConfiguredEncoder

  implicit val resultsWithCountsEncoder: Encoder[ResultsWithCountsDto] =
    deriveConfiguredEncoder

  implicit val resultsWithCountsDecoder: Decoder[ResultsWithCountsDto] =
    Decoder.failed(DecodingFailure("Not implemented", List.empty))

  private implicit val testResultsEncoder: Encoder[TestResultsDto] = new Encoder[TestResultsDto]() {

    implicit val contextIdPathPartDtoEncoder: Encoder[ContextIdPathPartDto] =
      deriveEncoder[ContextIdPathPartDto]
    implicit val contextIdEncoder: Encoder[ContextId] =
      deriveEncoder[ContextIdDto].contramap(ContextIdDto.from)

    implicit val nodeResult: Encoder[ResultContext[Json]] = encoderWithLegacyContextId(
      fieldName = "id",
      valueExtractor = _.id,
      underlying = Encoder.forProduct3("cid", "timestamp", "variables")(r => (r.id, r.timestamp, r.variables)),
    )

    implicit val expressionInvocationResult: Encoder[ExpressionEvaluationResult[Json]] = encoderWithLegacyContextId(
      fieldName = "contextId",
      valueExtractor = _.contextId,
      underlying =
        Encoder.forProduct4("cid", "timestamp", "name", "value")(r => (r.contextId, r.timestamp, r.name, r.value)),
    )

    implicit val externalInvocationResult: Encoder[ExternalServiceInvocationResult[Json]] = encoderWithLegacyContextId(
      fieldName = "contextId",
      valueExtractor = _.contextId,
      underlying =
        Encoder.forProduct4("cid", "timestamp", "name", "value")(r => (r.contextId, r.timestamp, r.name, r.value)),
    )

    implicit val nodeTransitionResultEncoder: Encoder[NodeTransitionResult] = Encoder.instance { value =>
      val baseFields: List[(String, Option[Json])] = List(
        "sourceNodeId"      -> Some(value.sourceNodeId.value.asJson),
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
            expressionEvaluationResults,
            externalServiceInvocationResults,
            exceptions,
            exceptionsByNodeId,
          ) =>
        Json.obj(
          "nodeResults" -> nodeResults
            .map(_.map { case (node, list) => node -> list.sortBy(_.id.legacyString) }.asJson)
            .getOrElse(Json.Null),
          "nodeTransitionResults" -> nodeTransitionResults
            .map(_.sortBy(r => (r.sourceNodeId, r.destinationNodeId)))
            .asJson,
          "expressionEvaluationResults" -> expressionEvaluationResults.map { case (node, list) =>
            node -> list.sortBy(_.contextId.legacyString)
          }.asJson,
          "externalServiceInvocationResults" -> externalServiceInvocationResults.map { case (node, list) =>
            node -> list.sortBy(_.contextId.legacyString)
          }.asJson,
          "exceptions" -> exceptions.sortBy(_.context.id.legacyString).asJson,
          "exceptionsByNodeId" -> exceptionsByNodeId.map { case (nodeId, exs) =>
            nodeId -> exs.sortBy(_.context.id.legacyString)
          }.asJson,
        )
    }

    // todo: remove when we no longer rely on legacy ContextId values on FE
    private def encoderWithLegacyContextId[T](
        fieldName: String,
        valueExtractor: T => ContextId,
        underlying: Encoder[T],
    ): Encoder[T] = {
      Encoder.instance { value =>
        underlying(value).deepMerge(Json.obj(fieldName -> valueExtractor(value).legacyString.asJson))
      }
    }

  }

  final case class ContextIdDto(
      nid: NodeId,
      tid: Long,
      idx: Long,
      path: List[ContextIdPathPartDto]
  )

  object ContextIdDto {

    def from(id: ContextId): ContextIdDto = {
      ContextIdDto(
        nid = id.originatingNodeId,
        tid = id.taskId,
        idx = id.index,
        path = id.path.map(t => ContextIdPathPartDto(t.nodeId, t.value)),
      )
    }

  }

  final case class ContextIdPathPartDto(n: NodeId, t: String)

}
