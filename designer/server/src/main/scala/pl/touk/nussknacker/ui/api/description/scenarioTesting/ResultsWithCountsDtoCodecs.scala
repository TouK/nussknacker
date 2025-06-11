package pl.touk.nussknacker.ui.api.description.scenarioTesting

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import io.circe.generic.semiauto.deriveEncoder
import pl.touk.nussknacker.engine.api.ContextId
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

    implicit val contextIdTransformationDtoEncoder: Encoder[ContextIdTransformationDto] =
      deriveEncoder[ContextIdTransformationDto]
    implicit val contextIdEncoder: Encoder[ContextId] =
      deriveEncoder[ContextIdDto].contramap(ContextIdDto.from)

    implicit val nodeResult: Encoder[ResultContext[Json]] = encoderWithLegacyContextId(
      fieldName = "id",
      valueExtractor = _.id,
      underlying = Encoder.forProduct3("cid", "timestamp", "variables")(r => (r.id, r.timestamp, r.variables)),
    )

    implicit val expressionInvocationResult: Encoder[ExpressionInvocationResult[Json]] = encoderWithLegacyContextId(
      fieldName = "contextId",
      valueExtractor = _.contextId,
      underlying =
        Encoder.forProduct4("cid", "timestamp", "name", "value")(r => (r.contextId, r.timestamp, r.name, r.value)),
    )

    implicit val externalInvocationResult: Encoder[ExternalInvocationResult[Json]] = encoderWithLegacyContextId(
      fieldName = "contextId",
      valueExtractor = _.contextId,
      underlying =
        Encoder.forProduct4("cid", "timestamp", "name", "value")(r => (r.contextId, r.timestamp, r.name, r.value)),
    )

    implicit val nodeTransitionResult: Encoder[NodeTransitionResult] = deriveConfiguredEncoder

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
            .map(_.map { case (node, list) => node -> list.sortBy(_.id.legacyString) }.asJson)
            .getOrElse(Json.Null),
          "nodeTransitionResults" -> nodeTransitionResults.asJson.deepDropNullValues,
          "invocationResults" -> invocationResults.map { case (node, list) =>
            node -> list.sortBy(_.contextId.legacyString)
          }.asJson,
          "externalInvocationResults" -> externalInvocationResults.map { case (node, list) =>
            node -> list.sortBy(_.contextId.legacyString)
          }.asJson,
          "exceptions" -> exceptions.sortBy(_.context.id.legacyString).asJson,
          "exceptionsByNodeId" -> exceptionsByNodeId.map { case (nodeId, exs) =>
            nodeId -> exs.sortBy(_.context.id.legacyString)
          }.asJson,
        )
    }

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
      nid: String,
      tid: Long,
      idx: Long,
      p: List[ContextIdTransformationDto]
  )

  object ContextIdDto {

    def from(id: ContextId): ContextIdDto = {
      ContextIdDto(
        nid = id.originatingNodeId,
        tid = id.taskId,
        idx = id.index,
        p = id.path.map(t => ContextIdTransformationDto(t.nodeId, t.value)),
      )
    }

  }

  final case class ContextIdTransformationDto(n: String, t: String)

}
