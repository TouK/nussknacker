package pl.touk.nussknacker.ui.api.description.scenarioTesting

import io.circe.{Decoder, DecodingFailure, Encoder, Json}
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import io.circe.generic.semiauto.deriveEncoder
import pl.touk.nussknacker.engine.api.{ContextId, ContextIdTransformation}
import pl.touk.nussknacker.engine.testmode.TestProcess.{
  ExceptionResult,
  ExpressionInvocationResult,
  ExternalInvocationResult,
  ResultContext
}

import scala.jdk.CollectionConverters._

object ResultsWithCountsDtoCodecs {

  import io.circe.syntax._
  import pl.touk.nussknacker.engine.api.CirceUtil._

  implicit val resultsWithCountsEncoder: Encoder[ResultsWithCountsDto] =
    deriveConfiguredEncoder

  implicit val resultsWithCountsDecoder: Decoder[ResultsWithCountsDto] =
    Decoder.failed(DecodingFailure("Not implemented", List.empty))

  private implicit val testResultsEncoder: Encoder[TestResultsDto] = new Encoder[TestResultsDto]() {

    implicit val contextId: Encoder[ContextId] = Encoder.encodeString.contramap(_.serialize)

    implicit val nodeResult: Encoder[ResultContext[Json]] =
      encoderWithDetailedContextId(deriveConfiguredEncoder[ResultContext[Json]], _.id)

    implicit val expressionInvocationResult: Encoder[ExpressionInvocationResult[Json]] =
      encoderWithDetailedContextId(deriveConfiguredEncoder[ExpressionInvocationResult[Json]], _.contextId)

    implicit val externalInvocationResult: Encoder[ExternalInvocationResult[Json]] =
      encoderWithDetailedContextId(deriveConfiguredEncoder[ExternalInvocationResult[Json]], _.contextId)

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
            .map(_.map { case (node, list) => node -> list.sortBy(_.id.serialize) }.asJson)
            .getOrElse(Json.Null),
          "nodeTransitionResults" -> nodeTransitionResults.asJson.deepDropNullValues,
          "invocationResults" -> invocationResults.map { case (node, list) =>
            node -> list.sortBy(_.contextId.serialize)
          }.asJson,
          "externalInvocationResults" -> externalInvocationResults.map { case (node, list) =>
            node -> list.sortBy(_.contextId.serialize)
          }.asJson,
          "exceptions" -> exceptions.sortBy(_.context.id.serialize).asJson,
          "exceptionsByNodeId" -> exceptionsByNodeId.map { case (nodeId, exs) =>
            nodeId -> exs.sortBy(_.context.id.serialize)
          }.asJson,
        )
    }

    private def encoderWithDetailedContextId[T](underlying: Encoder[T], f: T => ContextId): Encoder[T] = {
      implicit val contextIdTransformationDtoEncoder: Encoder[ContextIdTransformationDto] =
        deriveEncoder[ContextIdTransformationDto]
      implicit val contextIdEncoder: Encoder[ContextId] =
        deriveEncoder[ContextIdDto].contramap(ContextIdDto.from)
      Encoder.instance { value =>
        underlying(value).deepMerge(Json.obj("cid" -> contextIdEncoder(f(value))))
      }
    }

  }

  final case class ContextIdDto(
      sid: String,
      nid: String,
      tid: Long,
      idx: Long,
      t: List[ContextIdTransformationDto]
  ) {

    def contextId: ContextId =
      ContextId(
        scenarioId = sid,
        originatingNodeId = nid,
        taskId = tid,
        index = idx,
        transformations = t.map(t => ContextIdTransformation(t.n, t.t)).asJava,
      )

  }

  object ContextIdDto {

    def from(id: ContextId): ContextIdDto = {
      ContextIdDto(
        sid = id.scenarioId,
        nid = id.originatingNodeId,
        tid = id.taskId,
        idx = id.index,
        t = id.transformations.asScala.toList.map(t => ContextIdTransformationDto(t.nodeId, t.transformation)),
      )
    }

  }

  final case class ContextIdTransformationDto(n: String, t: String)

}
