package pl.touk.nussknacker.ui.api.description.scenarioTests

import io.circe.{Decoder, DecodingFailure, Encoder, Json, KeyEncoder}
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import pl.touk.nussknacker.engine.testmode.TestProcess.{
  Edge,
  ExceptionResult,
  ExpressionInvocationResult,
  ExternalInvocationResult,
  ResultContext,
  TestResults
}
import pl.touk.nussknacker.ui.process.test.ResultsWithCounts

object TestResultsCodecs {

  import io.circe.syntax._
  import pl.touk.nussknacker.engine.api.CirceUtil._

  implicit val resultsWithCountsEncoder: Encoder[ResultsWithCounts] =
    deriveConfiguredEncoder

  implicit val resultsWithCountsDecoder: Decoder[ResultsWithCounts] =
    Decoder.failed(DecodingFailure("Not implemented", List.empty))

  private implicit val testResultsEncoder: Encoder[TestResults[Json]] = new Encoder[TestResults[Json]]() {

    implicit val nodeResult: Encoder[ResultContext[Json]]                              = deriveConfiguredEncoder
    implicit val expressionInvocationResult: Encoder[ExpressionInvocationResult[Json]] = deriveConfiguredEncoder
    implicit val externalInvocationResult: Encoder[ExternalInvocationResult[Json]]     = deriveConfiguredEncoder

    // TODO: do we want more information here?
    implicit val throwableEncoder: Encoder[Throwable] = Encoder[Option[String]].contramap(th => Option(th.getMessage))
    implicit val exceptionResultEncoder: Encoder[ExceptionResult[Json]] = deriveConfiguredEncoder
    implicit val edgeIdKeyEncoder: KeyEncoder[Edge] =
      KeyEncoder.encodeKeyString.contramap(edge => edge.sourceNodeId + "->" + edge.destinationNodeId)

    override def apply(a: TestResults[Json]): Json = a match {
      case TestResults(
            nodeResults,
            nodeEdgeOutputResults,
            nodeDeadEndOutputResults,
            invocationResults,
            externalInvocationResults,
            exceptions
          ) =>
        Json.obj(
          "nodeResults" -> nodeResults.map { case (node, list) => node -> list.sortBy(_.id) }.asJson,
          "nodeEdgeOutputResults" -> nodeEdgeOutputResults.map { case (node, list) =>
            node -> list.sortBy(_.id)
          }.asJson,
          "nodeDeadEndOutputResults" -> nodeDeadEndOutputResults.map { case (node, list) =>
            node -> list.sortBy(_.id)
          }.asJson,
          "invocationResults" -> invocationResults.map { case (node, list) => node -> list.sortBy(_.contextId) }.asJson,
          "externalInvocationResults" -> externalInvocationResults.map { case (node, list) =>
            node -> list.sortBy(_.contextId)
          }.asJson,
          "exceptions" -> exceptions.sortBy(_.context.id).asJson
        )
    }

  }

}
